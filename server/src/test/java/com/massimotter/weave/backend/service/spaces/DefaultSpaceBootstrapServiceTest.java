package com.massimotter.weave.backend.service.spaces;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.DeploymentOrganizationProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.IdentityReferences;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.MemberAuthorization;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.ProviderMember;
import com.massimotter.weave.backend.model.*;
import com.massimotter.weave.backend.service.OrganizationIdentityContext;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.spaces.port.DefaultSpacePreparationPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.http.HttpStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultSpaceBootstrapServiceTest {
    private final KeycloakIdentityAdminClient keycloak = mock(KeycloakIdentityAdminClient.class);
    private final OrganizationIdentityContextResolver identities = mock(OrganizationIdentityContextResolver.class);
    private final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    private final DefaultSpacePreparationPort spaces = mock(DefaultSpacePreparationPort.class);
    private final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    private DefaultSpaceBootstrapService service;
    private static final String ISSUER = "https://auth.example.test/realms/weave";

    @BeforeEach void setUp() {
        var properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri(ISSUER);
        service = new DefaultSpaceBootstrapService(keycloak, identities, capabilities, spaces, audit, properties,
                new DeploymentOrganizationProperties("provider-org", "weave"));
        when(keycloak.configuredOrganizationId()).thenReturn("provider-org");
        when(identities.configuredOrganizationId()).thenReturn("org:configured");
        when(keycloak.members("provider-org")).thenReturn(List.of(member("actual-owner", List.of("owner"), true)));
        when(keycloak.memberAuthorization("provider-org", "actual-owner")).thenReturn(
                new MemberAuthorization("actual-owner", true, true, List.of("owner"), List.of("/owners")));
        when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(ready(), ready()));
        when(spaces.prepare(anyString(), anyString())).thenReturn(DefaultSpacePreparationPort.Result.CREATED);
    }

    @Test void actualOwnerContextIsVerifiedAndAuditedWithoutIdentityWrites() {
        var result = service.prepare();
        assertThat(result.outcome()).isEqualTo("CREATED");
        assertThat(result.ownerAccessVerified()).isTrue();
        assertThat(result.supportSafe()).isTrue();
        assertThat(result.containsSecretValues()).isFalse();
        assertThat(result.toString()).doesNotContain("actual-owner", "provider-org", "acct_");
        verify(spaces).prepare("org:configured", IdentityReferences.accountId(ISSUER, "actual-owner"));
        ArgumentCaptor<OrganizationIdentityContext> context = ArgumentCaptor.forClass(OrganizationIdentityContext.class);
        verify(capabilities).requireCapabilityForVerifiedMember(context.capture(), eq("admin.policy.edit"), eq("spaces"), eq("bootstrap"));
        assertThat(context.getValue().groups()).containsExactly("/owners");
        assertThat(context.getValue().issuer()).isEqualTo(ISSUER);
        verify(keycloak).configuredOrganizationId();
        verify(keycloak).members("provider-org");
        verify(keycloak).memberAuthorization("provider-org", "actual-owner");
        verifyNoMoreInteractions(keycloak);
        verify(audit).publish(argThat(event -> event.actorRef().equals("operator:default-space-bootstrap")
                && event.payload().get("result").equals("CREATED")));
    }

    @Test void noOwnerDisabledOwnerMultipleOwnersAndMixedRoleFailBeforeSpaceWrites() {
        for (var inventory : List.of(List.<ProviderMember>of(),
                List.of(member("owner", List.of("owner"), false)),
                List.of(member("one", List.of("owner"), true), member("two", List.of("owner"), true)),
                List.of(member("owner", List.of("owner", "admin"), true)),
                List.of(member("member", List.of("member"), true)))) {
            when(keycloak.members("provider-org")).thenReturn(inventory);
            assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                    error -> assertThat(error.code()).isEqualTo("default-space-owner-unavailable"));
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void liveRecheckCannotChangeDisableOrBroadenSelectedOwner() {
        for (var owner : List.of(new MemberAuthorization("actual-owner", true, false, List.of("owner"), List.of()),
                new MemberAuthorization("actual-owner", false, true, List.of("owner"), List.of()),
                new MemberAuthorization("other-owner", true, true, List.of("owner"), List.of()),
                new MemberAuthorization("actual-owner", true, true, List.of("owner", "member"), List.of()))) {
            when(keycloak.memberAuthorization("provider-org", "actual-owner")).thenReturn(owner);
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void unavailableInventoryDoesNotExposeProviderFailureOrMutate() {
        when(keycloak.members("provider-org")).thenThrow(new IllegalStateException("private provider payload"));
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-owner-verification-unavailable"))
                .hasMessageNotContaining("private provider payload");
        verifyNoInteractions(spaces, audit);
    }

    @Test void wrongConfiguredOrganizationFailsClosed() {
        when(keycloak.configuredOrganizationId()).thenReturn("other-org");
        assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        verify(keycloak, never()).members(anyString());
        verifyNoInteractions(spaces, audit);
    }

    @Test void currentAdminPolicyDenialPreventsAllSpaceMutation() {
        doThrow(new ApiErrorException(HttpStatus.FORBIDDEN, "capability-policy-blocked", "denied", Map.of()))
                .when(capabilities).requireCapabilityForVerifiedMember(any(), eq("admin.policy.edit"), anyString(), anyString());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("capability-policy-blocked"));
        verifyNoInteractions(spaces, audit);
    }

    @Test void disabledBlockedUnavailableAndDegradedSurfacesPreventPreparation() {
        for (var state : List.of(new WorkspaceCapabilityStatusResponse(false, WorkspaceCapabilityReadiness.READY,
                        WorkspaceCapabilityPolicyState.DISABLED, "policy", "disabled", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.READY,
                        WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "policy", "denied", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.UNAVAILABLE,
                        WorkspaceCapabilityPolicyState.UNAVAILABLE, "policy", "unavailable", List.of()),
                new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.DEGRADED,
                        WorkspaceCapabilityPolicyState.ALLOWED, "policy", "degraded", List.of()))) {
            when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(state, ready()));
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
            when(capabilities.snapshotForVerifiedMember(any())).thenReturn(snapshot(ready(), state));
            assertThatThrownBy(service::prepare).isInstanceOf(ApiErrorException.class);
        }
        verifyNoInteractions(spaces, audit);
    }

    @Test void existingAccessIsVerifiedAndConflictIsNeverRestored() {
        when(spaces.prepare(anyString(), anyString())).thenReturn(DefaultSpacePreparationPort.Result.VERIFIED);
        assertThat(service.prepare().outcome()).isEqualTo("VERIFIED");
        when(spaces.prepare(anyString(), anyString())).thenThrow(new DefaultSpacePreparationPort.Conflict());
        assertThatThrownBy(service::prepare).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("default-space-owner-access-conflict"));
    }

    private static ProviderMember member(String subject, List<String> roles, boolean enabled) {
        return new ProviderMember(subject, "private@example.test", "Private name", roles, List.of(), enabled);
    }
    private static WorkspaceCapabilityStatusResponse ready() {
        return new WorkspaceCapabilityStatusResponse(true, WorkspaceCapabilityReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED, "policy", "ready", List.of());
    }
    private static WorkspaceCapabilitiesResponse snapshot(WorkspaceCapabilityStatusResponse files,
            WorkspaceCapabilityStatusResponse calendar) {
        return new WorkspaceCapabilitiesResponse(ready(), ready(), files, calendar, ready(), ready());
    }
}
