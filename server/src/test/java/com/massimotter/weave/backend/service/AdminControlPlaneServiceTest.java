package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDesiredState;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AdminControlPlaneServiceTest {

    @Test
    void providerAndPolicyMutationsRequireEffectiveOwnerOrAdminPolicyServerSide() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = new AdminControlPlaneService(
                mock(ProviderRegistry.class),
                workspaceCapabilityService(),
                new InMemoryProviderSelectionRepository(),
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));
        CapabilityWhitelistUpdateRequest request = new CapabilityWhitelistUpdateRequest(
                "workspace-admin",
                List.of("admin.policy.edit", "admin.provider.configure"),
                "maintain recovery admin policy");

        assertThatThrownBy(() -> service.updateWhitelist(request, jwt("operator")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                    assertThat(exception.details()).containsEntry("requiredCapability", "admin.policy.edit");
                    assertThat(exception.details()).containsEntry("diagnosticsRedacted", true);
                });
        assertThatThrownBy(() -> service.updateWhitelist(request, jwt("member")))
                .isInstanceOfSatisfying(ApiErrorException.class, exception -> {
                    assertThat(exception.status().value()).isEqualTo(403);
                    assertThat(exception.code()).isEqualTo("capability-policy-blocked");
                });

        var response = service.updateWhitelist(request, jwt("admin"));

        assertThat(response.denyByDefault()).isTrue();
        assertThat(auditPublisher.events())
                .extracting(event -> event.action())
                .containsExactly(AuditAction.ADMIN_POLICY_UPDATED);
        assertThat(auditPublisher.events().get(0).payload())
                .containsEntry("profileKey", "workspace-admin")
                .containsEntry("denyByDefault", true);
    }


    @Test
    void adminAndOperatorCanSimulateEffectiveProviderPolicyBeforeChanges() throws Exception {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        EffectivePolicySimulationRequest request = new EffectivePolicySimulationRequest(
                "issuer+subject:https://auth.example.invalid/realms/weave#member-123",
                "weave-dogfood",
                List.of("member"),
                List.of("weave-board-editors"),
                List.of("chat.send", "boards.update_task", "admin.provider.configure", "weaver.enabled"),
                "simulate before #233 dry-run/apply with Bearer operator-token and secretref://weave/provider/keycloak");

        var adminResponse = service.simulateEffectivePolicy(request, jwt("admin"));
        var operatorResponse = service.simulateEffectivePolicy(request, jwt("operator"));

        assertThat(adminResponse.supportSafe()).isTrue();
        assertThat(adminResponse.unknownInputsFailClosed()).isFalse();
        assertThat(adminResponse.weaverDefaultDisabled()).isTrue();
        assertThat(adminResponse.grantedCapabilities()).containsExactly("boards.update_task", "chat.send");
        assertThat(adminResponse.capabilityStates()).extracting(state -> state.state())
                .containsOnly("ready", "disabled", "policy-blocked");
        assertThat(adminResponse.capabilityStates()).filteredOn(state -> state.capability().equals("weaver.enabled"))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.state()).isEqualTo("disabled");
                    assertThat(state.reasonCode()).isEqualTo("weaver-default-disabled");
                });
        assertThat(operatorResponse.grantedCapabilities()).containsExactly("boards.update_task", "chat.send");

        assertThat(auditPublisher.events()).hasSize(2);
        assertThat(auditPublisher.events()).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.EFFECTIVE_POLICY_SIMULATED);
            assertThat(event.payload())
                    .containsEntry("roleCount", 1)
                    .containsEntry("groupCount", 1)
                    .containsEntry("requestedCapabilityCount", 4)
                    .containsEntry("unknownInputCount", 0)
                    .containsEntry("supportSafe", true)
                    .containsEntry("reasonProvided", true);
            assertThat(event.payload()).doesNotContainKeys("reason", "subject", "organizationId");
        });
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(auditPublisher.events()))
                .doesNotContain("operator-token", "secretref://", "simulate before #233", "member-123");
    }

    @Test
    void unknownSimulationInputsFailClosedForEveryRequestedCapability() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        EffectivePolicySimulationRequest request = new EffectivePolicySimulationRequest(
                "alice@example.com",
                "weave-dogfood",
                List.of("super-admin", "not mapped"),
                List.of("finance-admins"),
                List.of("chat.read", "provider.internal.token", "Bearer raw-token"),
                "unknown provider import preview");

        var response = service.simulateEffectivePolicy(request, jwt("admin"));

        assertThat(response.subject()).isEqualTo("identity-ref-redacted");
        assertThat(response.unknownInputsFailClosed()).isTrue();
        assertThat(response.grantedCapabilities()).isEmpty();
        assertThat(response.roles()).isEmpty();
        assertThat(response.groups()).isEmpty();
        assertThat(response.requestedCapabilities()).containsExactly("chat.read");
        assertThat(response.deniedInputs()).containsExactly(
                "invalid-capability",
                "invalid-role",
                "unknown-capability",
                "unknown-group",
                "unknown-role");
        assertThat(response.capabilityStates()).extracting(state -> state.state()).containsOnly("policy-blocked");
        assertThat(response.capabilityStates()).extracting(state -> state.reasonCode())
                .containsOnly("unknown-identity-inputs-fail-closed");
        assertThat(response.toString()).doesNotContain("super-admin", "not mapped", "finance-admins", "provider.internal.token", "raw-token");
    }

    @Test
    void checkedInSimulationFixtureIsSupportSafeAndContractShaped() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream input = getClass().getResourceAsStream("/effective-policy-simulation/admin-operator-preview.json")) {
            assertThat(input).isNotNull();
            JsonNode fixture = objectMapper.readTree(input);

            assertThat(fixture.path("supportSafe").asBoolean()).isTrue();
            assertThat(fixture.path("weaverDefaultDisabled").asBoolean()).isTrue();
            assertThat(fixture.path("unknownInputsFailClosed").asBoolean()).isFalse();
            assertThat(fixture.path("subject").asText()).startsWith("issuer+subject:");
            assertThat(fixture.path("capabilityStates")).allSatisfy(state ->
                    assertThat(state.path("state").asText()).isIn("ready", "disabled", "degraded", "policy-blocked"));
            assertThat(objectMapper.writeValueAsString(fixture))
                    .doesNotContain("@", "client_secret", "Authorization", "Bearer ", "access_token", "secretref://", "keycloak-realm");
        }
    }


    @Test
    void guardedIdentityRealmApplyAcceptsSafePlanOnlyWithConfirmationAndRetainedAdminKey() throws Exception {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        IdentityRealmDesiredState safeState = safeRealmState();

        var missingConfirmation = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeState,
                safeState,
                "reviewed only",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "safe apply review"), jwt("admin"));

        assertThat(missingConfirmation.decision()).isEqualTo("blocked");
        assertThat(missingConfirmation.applied()).isFalse();
        assertThat(missingConfirmation.providerMutationPerformed()).isFalse();
        assertThat(missingConfirmation.blockedReasons()).contains("explicit confirmation phrase is required");

        var unsafeAdminKey = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("alice@example.com"),
                null,
                "safe apply review"), jwt("admin"));

        assertThat(unsafeAdminKey.decision()).isEqualTo("blocked");
        assertThat(unsafeAdminKey.lastAdminGuardPassed()).isFalse();
        assertThat(unsafeAdminKey.blockedReasons())
                .contains("last-admin guard requires at least one retained immutable admin identity key");

        var accepted = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "safe apply review with Bearer token-that-must-not-leak"), jwt("admin"));

        assertThat(accepted.decision()).isEqualTo("accepted");
        assertThat(accepted.executionMode()).isEqualTo("guarded-provider-apply-decision-only");
        assertThat(accepted.applied()).isFalse();
        assertThat(accepted.providerMutationPerformed()).isFalse();
        assertThat(accepted.lastAdminGuardPassed()).isTrue();
        assertThat(accepted.rollbackEvidenceRequired()).isFalse();
        assertThat(accepted.blockedReasons()).isEmpty();
        assertThat(accepted.changes()).allSatisfy(change -> assertThat(change.classification()).isEqualTo("safe"));
        assertThat(accepted.nextActions()).anySatisfy(action -> assertThat(action).contains("no live provider mutation"));

        assertThat(auditPublisher.events()).extracting(event -> event.action())
                .contains(AuditAction.IDENTITY_REALM_APPLY_GUARDED);
        var auditJson = new ObjectMapper().findAndRegisterModules().writeValueAsString(auditPublisher.events());
        assertThat(auditJson)
                .contains("IDENTITY_REALM_APPLY_GUARDED", "user:admin-123", "candidateRef", "planRef", "accepted-without-provider-mutation")
                .doesNotContain("alice@example.com", "safe apply review", "token-that-must-not-leak", "issuer+subject:https://auth.example.invalid/realms/weave#admin-123");
    }

    @Test
    void guardedIdentityRealmApplyBlocksRiskyWithoutApprovalAndRollbackEvidence() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        IdentityRealmDesiredState riskyState = riskyRealmState();

        var missingApproval = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeRealmState(),
                riskyState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "risky apply with secretref://raw/ref"), jwt("admin"));

        assertThat(missingApproval.decision()).isEqualTo("blocked");
        assertThat(missingApproval.applied()).isFalse();
        assertThat(missingApproval.providerMutationPerformed()).isFalse();
        assertThat(missingApproval.rollbackEvidenceRequired()).isTrue();
        assertThat(missingApproval.blockedReasons()).contains(
                "risky changes require approveRisky=true",
                "rollback/restore evidence ref is required for risky or destructive apply");
        assertThat(missingApproval.nextActions()).anySatisfy(action -> assertThat(action).contains("effective policy simulation"));

        var accepted = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeRealmState(),
                riskyState,
                "APPLY WEAVE IDENTITY REALM",
                true,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                "rollback-evidence:ticket-370-support-safe",
                "risky apply with Bearer raw-token"), jwt("admin"));

        assertThat(accepted.decision()).isEqualTo("accepted");
        assertThat(accepted.applied()).isFalse();
        assertThat(accepted.providerMutationPerformed()).isFalse();
        assertThat(accepted.rollbackEvidenceAccepted()).isTrue();
        assertThat(accepted.changes()).extracting(change -> change.classification()).contains("risky");
    }

    @Test
    void guardedIdentityRealmApplyBlocksDestructiveWithoutApprovalsAndWhenProviderUnavailable() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        IdentityRealmDesiredState current = riskyRealmState();
        IdentityRealmDesiredState desired = safeRealmState();

        var missingGuards = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                current,
                desired,
                "APPLY WEAVE IDENTITY REALM",
                true,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "destructive review"), jwt("admin"));

        assertThat(missingGuards.decision()).isEqualTo("blocked");
        assertThat(missingGuards.applied()).isFalse();
        assertThat(missingGuards.providerMutationPerformed()).isFalse();
        assertThat(missingGuards.blockedReasons()).contains(
                "destructive changes require approveDestructive=true",
                "provider destructive apply is not available for this contract",
                "rollback/restore evidence ref is required for risky or destructive apply");
        assertThat(missingGuards.changes()).extracting(change -> change.classification()).contains("destructive");
        assertThat(missingGuards.nextActions()).anySatisfy(action -> assertThat(action).contains("destructive changes as unavailable"));

        var stillUnavailable = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                current,
                desired,
                "APPLY WEAVE IDENTITY REALM",
                true,
                true,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                "restore-evidence:backup-370-support-safe",
                "destructive review"), jwt("admin"));

        assertThat(stillUnavailable.decision()).isEqualTo("blocked");
        assertThat(stillUnavailable.blockedReasons()).contains("provider destructive apply is not available for this contract");
        assertThat(stillUnavailable.blockedReasons()).contains("destructive realm changes are blocked in this dry-run-only slice");
        assertThat(stillUnavailable.applied()).isFalse();
        assertThat(stillUnavailable.providerMutationPerformed()).isFalse();
    }


    @Test
    void checkedInApplyFixtureIsSupportSafeDecisionOnlyEvidence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream input = getClass().getResourceAsStream("/identity-realm-apply/guarded-safe-accepted.json")) {
            assertThat(input).isNotNull();
            JsonNode fixture = objectMapper.readTree(input);

            assertThat(fixture.path("supportSafe").asBoolean()).isTrue();
            assertThat(fixture.path("decision").asText()).isEqualTo("accepted");
            assertThat(fixture.path("executionMode").asText()).isEqualTo("guarded-provider-apply-decision-only");
            assertThat(fixture.path("applied").asBoolean()).isFalse();
            assertThat(fixture.path("providerMutationPerformed").asBoolean()).isFalse();
            assertThat(objectMapper.writeValueAsString(fixture))
                    .doesNotContain("@", "client_secret", "Authorization", "Bearer ", "access_token", "secretref://", "rollbackEvidenceRef", "issuer+subject:");
        }
    }

    private AdminControlPlaneService adminControlPlaneService(InMemoryAuditEventPublisher auditPublisher) {
        return new AdminControlPlaneService(
                mock(ProviderRegistry.class),
                workspaceCapabilityService(),
                new InMemoryProviderSelectionRepository(),
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.local/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }


    private IdentityRealmDesiredState safeRealmState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.local/callback"),
                        List.of("owner", "admin", "member"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member"),
                List.of("weave-board-editors"),
                List.of("openid", "profile", "email", "weave:workspace"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.local/callback"),
                List.of(new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("weave-board-editors"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("operator"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of(),
                List.of());
    }

    private IdentityRealmDesiredState riskyRealmState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.local/callback", "http://localhost:8080/*"),
                        List.of("owner", "admin", "member"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member"),
                List.of("weave-board-editors"),
                List.of("openid", "profile", "email", "weave:workspace"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.local/callback", "http://localhost:8080/*"),
                List.of(new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("weave-board-editors"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("operator"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("subject:owner:current", "last-admin recovery", true, List.of("owner"))),
                List.of("subject:owner:current"),
                "sub",
                List.of(),
                List.of());
    }

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(role + "-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant", "weave-dogfood")
                .claim("realm_access", Map.of("roles", List.of(role)))
                .claim("groups", List.of())
                .build();
    }
}
