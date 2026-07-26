package com.massimotter.weave.backend.agentruntime.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeCellState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCellRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileRepository;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileVerifier;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpWorkloadAuthorizationServiceTest {
  private static final Instant NOW = Instant.parse("2026-07-20T12:00:00Z");
  private static final String ISSUER = "https://auth.weave.test/realms/weave";
  private static final String API_RESOURCE = "https://api.weave.test/api";
  private static final String SUBJECT = "service-account-cell-subject";
  private static final String CLIENT = "weaver-cell-test";
  private static final String PROFILE_ID = "rp_test";
  private static final String PROFILE_HASH = "sha256:" + "2".repeat(64);
  private static final String ENTITLEMENT_REVISION = "sha256:" + "3".repeat(64);
  private static final String SOURCE_GROUP = "sha256:" + "4".repeat(64);
  private static final String CAPABILITY_REVISION = "sha256:" + "5".repeat(64);
  private static final RuntimeMemberBinding MEMBER =
      new RuntimeMemberBinding(ISSUER, "member-subject");

  private RuntimeCellRepository cells;
  private RuntimeProfileRepository profiles;
  private RuntimeProfileVerifier verifier;
  private RuntimeGovernanceRepository governance;
  private RuntimeWorkloadIdentityAdmin identities;
  private RuntimeEntitlementAuthority entitlementAuthority;
  private McpWorkloadAuthorizationService service;
  private RuntimeCell cell;

  @BeforeEach
  void setUp() {
    cells = mock(RuntimeCellRepository.class);
    profiles = mock(RuntimeProfileRepository.class);
    verifier = mock(RuntimeProfileVerifier.class);
    governance = mock(RuntimeGovernanceRepository.class);
    identities = mock(RuntimeWorkloadIdentityAdmin.class);
    entitlementAuthority = mock(RuntimeEntitlementAuthority.class);
    cell = cell();
    SignedRuntimeProfile envelope = envelope();
    RuntimeProfile profile = profile();
    when(cells.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(cell));
    when(profiles.findCurrentForWorkload(PROFILE_HASH, ISSUER, SUBJECT, CLIENT, NOW))
        .thenReturn(Optional.of(envelope));
    when(verifier.verify(envelope, NOW)).thenReturn(profile);
    when(governance.findEffectiveRevision("org:test", "person:test", ENTITLEMENT_REVISION, NOW))
        .thenReturn(Optional.of(entitlement()));
    when(entitlementAuthority.observe(any())).thenReturn(observation());
    doNothing().when(identities).requireCurrentBinding(any());
    service =
        new McpWorkloadAuthorizationService(
            cells,
            profiles,
            verifier,
            governance,
            identities,
            entitlementAuthority,
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @Test
  void resolvesTheGoverningMemberOnlyFromCurrentServerOwnedState() {
    var principal = service.authorize(token());

    assertThat(principal.workloadSubject()).isEqualTo(SUBJECT);
    assertThat(principal.workloadClientId()).isEqualTo(CLIENT);
    assertThat(principal.mcpEdgeClientId()).isEqualTo("weave-mcp-server");
    assertThat(principal.memberBinding()).isEqualTo(MEMBER);
    assertThat(principal.scopes()).containsExactly("calendar.read");
    assertThat(principal.visibleToolClasses()).containsExactly("calendar.read");
    assertThat(principal.authorizationExpiresAt()).isEqualTo(NOW.plusSeconds(30));
    verify(identities).requireCurrentBinding(any());
    verify(entitlementAuthority).observe(any());
  }

  @Test
  void failsClosedWhenLiveKeycloakEntitlementCannotBeObserved() {
    when(entitlementAuthority.observe(any()))
        .thenThrow(new RuntimeEntitlementAuthorityException("private provider diagnostic"));

    assertThatThrownBy(() -> service.authorize(token()))
        .isInstanceOfSatisfying(
            McpWorkloadAuthorizationException.class,
            failure -> assertThat(failure.authorityUnavailable()).isTrue())
        .hasMessageNotContaining("private provider diagnostic")
        .hasMessageNotContaining("exchanged-secret-token");
  }

  @Test
  void rejectsRevokedOrCrossBoundCellsBeforeDomainAccess() {
    RuntimeCell revoked =
        new RuntimeCell(
            cell.recordId(),
            cell.organizationRef(),
            cell.personRef(),
            cell.memberBinding(),
            cell.cellRef(),
            cell.workloadBinding(),
            RuntimeEntitlementState.REVOKED,
            cell.entitlementRevision(),
            RuntimeCellState.REVOKING,
            cell.observedState(),
            cell.runtimeProfileId(),
            cell.runtimeProfileHash(),
            cell.workspaceRevision(),
            cell.workspaceManifestRef(),
            cell.runtimeStateStoreRef(),
            cell.fencingEpoch(),
            null,
            null,
            cell.version(),
            cell.auditRef(),
            cell.createdAt(),
            cell.updatedAt());
    when(cells.findByWorkload(ISSUER, SUBJECT)).thenReturn(Optional.of(revoked));

    assertThatThrownBy(() -> service.authorize(token()))
        .isInstanceOf(McpWorkloadAuthorizationException.class)
        .hasMessageNotContaining(SUBJECT)
        .hasMessageNotContaining("member-subject")
        .hasMessageNotContaining("exchanged-secret-token");
  }

  private static ExchangedWorkloadToken token() {
    return new ExchangedWorkloadToken(
        ISSUER,
        SUBJECT,
        "weave-mcp-server",
        Set.of("calendar.read"),
        NOW.minusSeconds(1),
        NOW.plusSeconds(30),
        "exchange-jti");
  }

  private static RuntimeCell cell() {
    return new RuntimeCell(
        UUID.randomUUID(),
        "org:test",
        "person:test",
        MEMBER,
        "cell:test",
        new RuntimeWorkloadBinding(
            ISSUER,
            SUBJECT,
            CLIENT,
            RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
            "credentialref://weave/agent-runtime/cells/" + CLIENT),
        RuntimeEntitlementState.ENTITLED,
        ENTITLEMENT_REVISION,
        RuntimeCellState.STARTING,
        RuntimeCellState.ABSENT,
        PROFILE_ID,
        PROFILE_HASH,
        "workspace:1",
        "webdav-manifest:workspace:1",
        "runtime-state://org/test/person/test/state/1",
        0,
        null,
        null,
        1,
        "audit:test",
        NOW.minusSeconds(60),
        NOW.minusSeconds(1));
  }

  private static SignedRuntimeProfile envelope() {
    return new SignedRuntimeProfile(
        "protected",
        "payload",
        "A".repeat(86),
        PROFILE_HASH,
        PROFILE_ID,
        "cell:test",
        "key-test",
        NOW.minusSeconds(60),
        NOW.plusSeconds(60));
  }

  private static RuntimeProfile profile() {
    return new RuntimeProfile(
        RuntimeProfile.VERSION,
        PROFILE_ID,
        "org:test",
        "person:test",
        MEMBER,
        "cell:test",
        new RuntimeProfile.WorkloadIdentity(
            ISSUER,
            SUBJECT,
            CLIENT,
            "weaver-runtime",
            RuntimeProfile.AuthenticationMethod.PRIVATE_KEY_JWT),
        NOW.minusSeconds(60),
        NOW.plusSeconds(60),
        ENTITLEMENT_REVISION,
        "workspace:1",
        "webdav-manifest:workspace:1",
        "runtime-state://org/test/person/test/state/1",
        true,
        new RuntimeProfile.ModelPolicy(List.of(), List.of(), List.of(), null, null),
        new RuntimeProfile.MatrixPolicy(
            "matrix-account:test",
            "matrix-facade:test",
            null,
            List.of(),
            RuntimeProfile.AutoJoin.OFF,
            true),
        new RuntimeProfile.McpPolicy(
            List.of(
                new RuntimeProfile.McpServer(
                    "weave-domain-tools",
                    "https://api.weave.test/mcp",
                    "https://api.weave.test/mcp",
                    "io.modelcontextprotocol/oauth-client-credentials",
                    "client_credentials",
                    List.of("mcp:tools", "calendar.read"),
                    "credentialref://weave/mcp/test",
                    List.of("calendar.read"))),
            List.of("calendar.read")),
        new RuntimeProfile.ApprovalPolicy(
            "openclaw",
            new RuntimeProfile.PluginRouting(
                false, RuntimeProfile.PluginRoutingMode.LOCAL_ONLY, List.of()),
            RuntimeProfile.ExecMode.DENY,
            RuntimeProfile.PersistentTrustPolicy.DISABLED),
        new RuntimeProfile.SandboxPolicy(
            RuntimeProfile.SandboxMode.REQUIRED,
            RuntimeProfile.NetworkPolicy.DENY,
            List.of(),
            RuntimeProfile.FilesystemPolicy.WORKSPACE_ONLY,
            List.of()),
        new RuntimeProfile.AutomationPolicy(false, RuntimeProfile.SchedulePolicy.DISABLED));
  }

  private static RuntimeEntitlementRef entitlement() {
    return new RuntimeEntitlementRef(
        UUID.randomUUID(),
        "entitlement:" + "1".repeat(64),
        ENTITLEMENT_REVISION,
        "org:test",
        "person:test",
        MEMBER,
        "keycloak",
        SOURCE_GROUP,
        CAPABILITY_REVISION,
        RuntimeEntitlementState.ENTITLED,
        NOW.minusSeconds(60),
        NOW.minusSeconds(1),
        NOW.plusSeconds(60),
        null,
        null,
        "audit:entitlement",
        NOW.minusSeconds(60),
        NOW.minusSeconds(1));
  }

  private static RuntimeEntitlementObservation observation() {
    return new RuntimeEntitlementObservation(
        "org:test",
        "person:test",
        MEMBER,
        "keycloak",
        SOURCE_GROUP,
        CAPABILITY_REVISION,
        NOW,
        NOW.plusSeconds(45));
  }
}
