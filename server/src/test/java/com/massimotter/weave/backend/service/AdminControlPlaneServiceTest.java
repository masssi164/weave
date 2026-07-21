package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.audit.JdbcAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyProperties;
import com.massimotter.weave.backend.identity.realm.IdentityRealmApplyRequest;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDesiredState;
import com.massimotter.weave.backend.identity.realm.IdentityRealmDryRunRequest;
import com.massimotter.weave.backend.identity.realm.InMemoryIdentityRealmEvidenceRepository;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmDryRunProvider;
import com.massimotter.weave.backend.identity.realm.KeycloakRealmLiveApplyAdapter;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.service.migration.InMemoryMigrationRunEvidenceRepository;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
    void jdbcAuditPublisherFeedsAdminAuditReadback() {
        JdbcAuditEventPublisher auditPublisher = new JdbcAuditEventPublisher(new JdbcTemplate(migratedDataSource()));
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        CapabilityWhitelistUpdateRequest request = new CapabilityWhitelistUpdateRequest(
                "workspace-admin",
                List.of("admin.policy.edit", "admin.provider.configure"),
                "prove jdbc audit readback");

        service.updateWhitelist(request, jwt("admin"));

        assertThat(service.auditEvents(jwt("admin")))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.action()).isEqualTo("admin.policy.updated");
                    assertThat(event.idempotencyKey()).startsWith("admin-policy-");
                    assertThat(event.payload())
                            .containsEntry("profileKey", "workspace-admin")
                            .containsEntry("denyByDefault", true);
                });
    }


    @Test
    void adminCanSimulateEffectiveProviderPolicyBeforeChanges() throws Exception {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        EffectivePolicySimulationRequest request = new EffectivePolicySimulationRequest(
                "issuer+subject:https://auth.example.invalid/realms/weave#member-123",
                "weave-dogfood",
                List.of("member"),
                List.of("weave-board-editors"),
                List.of("chat.send", "boards.update_task", "admin.provider.configure", "agent-runtime.entitled"),
                "simulate before #233 dry-run/apply with Bearer operator-token and secretref://weave/provider/keycloak");

        var adminResponse = service.simulateEffectivePolicy(request, jwt("admin"));

        assertThat(adminResponse.supportSafe()).isTrue();
        assertThat(adminResponse.unknownInputsFailClosed()).isFalse();
        assertThat(adminResponse.agentRuntimeEntitlementRequired()).isTrue();
        assertThat(adminResponse.grantedCapabilities()).containsExactly("boards.update_task", "chat.send");
        assertThat(adminResponse.capabilityStates()).extracting(state -> state.state())
                .containsOnly("ready", "disabled", "policy-blocked");
        assertThat(adminResponse.capabilityStates()).filteredOn(state -> state.capability().equals("agent-runtime.entitled"))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.state()).isEqualTo("disabled");
                    assertThat(state.reasonCode()).isEqualTo("agent-runtime-entitlement-required");
                });
        assertThat(auditPublisher.events()).hasSize(1);
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
            assertThat(fixture.path("agentRuntimeEntitlementRequired").asBoolean()).isTrue();
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

        var missingConfirmation = service.applyIdentityRealm(applyRequest(service,
                safeState,
                safeState,
                "reviewed only",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "safe apply review",
                jwt("admin")), jwt("admin"));

        assertThat(missingConfirmation.decision()).isEqualTo("blocked");
        assertThat(missingConfirmation.applied()).isFalse();
        assertThat(missingConfirmation.providerMutationPerformed()).isFalse();
        assertThat(missingConfirmation.blockedReasons()).contains("explicit confirmation phrase is required");

        var unsafeAdminKey = service.applyIdentityRealm(applyRequest(service,
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("alice@example.com"),
                null,
                "safe apply review",
                jwt("admin")), jwt("admin"));

        assertThat(unsafeAdminKey.decision()).isEqualTo("blocked");
        assertThat(unsafeAdminKey.lastAdminGuardPassed()).isFalse();
        assertThat(unsafeAdminKey.blockedReasons())
                .contains("last-admin guard requires at least one retained immutable admin identity key");

        var missingRetainedAdminProof = service.applyIdentityRealm(applyRequest(service,
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#other-admin"),
                null,
                "safe apply review",
                jwt("admin")), jwt("admin"));

        assertThat(missingRetainedAdminProof.decision()).isEqualTo("blocked");
        assertThat(missingRetainedAdminProof.lastAdminGuardPassed()).isFalse();
        assertThat(missingRetainedAdminProof.blockedReasons())
                .contains("last-admin guard requires at least one retained immutable admin identity key");

        var accepted = service.applyIdentityRealm(applyRequest(service,
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "safe apply review with Bearer token-that-must-not-leak",
                jwt("admin")), jwt("admin"));

        assertThat(accepted.decision()).isEqualTo("accepted");
        assertThat(accepted.executionMode()).isEqualTo("guarded-provider-live-apply-disabled");
        assertThat(accepted.applied()).isFalse();
        assertThat(accepted.providerMutationPerformed()).isFalse();
        assertThat(accepted.lastAdminGuardPassed()).isTrue();
        assertThat(accepted.rollbackEvidenceRequired()).isFalse();
        assertThat(accepted.blockedReasons()).isEmpty();
        assertThat(accepted.changes()).allSatisfy(change -> assertThat(change.classification()).isEqualTo("safe"));
        assertThat(accepted.nextActions()).anySatisfy(action -> assertThat(action).contains("Live Keycloak realm apply is disabled"));

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

        var missingApproval = service.applyIdentityRealm(applyRequest(service,
                safeRealmState(),
                riskyState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "risky apply with secretref://raw/ref",
                jwt("admin")), jwt("admin"));

        assertThat(missingApproval.decision()).isEqualTo("blocked");
        assertThat(missingApproval.applied()).isFalse();
        assertThat(missingApproval.providerMutationPerformed()).isFalse();
        assertThat(missingApproval.rollbackEvidenceRequired()).isTrue();
        assertThat(missingApproval.blockedReasons()).contains(
                "risky changes require approveRisky=true",
                "rollback/restore evidence ref is required for risky or destructive apply");
        assertThat(missingApproval.nextActions()).anySatisfy(action -> assertThat(action).contains("effective policy simulation"));

        var accepted = service.applyIdentityRealm(applyRequest(service,
                safeRealmState(),
                riskyState,
                "APPLY WEAVE IDENTITY REALM",
                true,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                "rollback-evidence:ticket-370-support-safe",
                "risky apply with Bearer raw-token",
                jwt("admin")), jwt("admin"));

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

        var missingGuards = service.applyIdentityRealm(applyRequest(service,
                current,
                desired,
                "APPLY WEAVE IDENTITY REALM",
                true,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "destructive review",
                jwt("admin")), jwt("admin"));

        assertThat(missingGuards.decision()).isEqualTo("blocked");
        assertThat(missingGuards.applied()).isFalse();
        assertThat(missingGuards.providerMutationPerformed()).isFalse();
        assertThat(missingGuards.blockedReasons()).contains(
                "destructive changes require approveDestructive=true",
                "provider destructive apply is not available for this contract",
                "rollback/restore evidence ref is required for risky or destructive apply");
        assertThat(missingGuards.changes()).extracting(change -> change.classification()).contains("destructive");
        assertThat(missingGuards.nextActions()).anySatisfy(action -> assertThat(action).contains("destructive changes as unavailable"));

        var stillUnavailable = service.applyIdentityRealm(applyRequest(service,
                current,
                desired,
                "APPLY WEAVE IDENTITY REALM",
                true,
                true,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                "restore-evidence:backup-370-support-safe",
                "destructive review",
                jwt("admin")), jwt("admin"));

        assertThat(stillUnavailable.decision()).isEqualTo("blocked");
        assertThat(stillUnavailable.blockedReasons()).contains("provider destructive apply is not available for this contract");
        assertThat(stillUnavailable.blockedReasons()).contains("destructive realm changes are blocked in this dry-run-only slice");
        assertThat(stillUnavailable.applied()).isFalse();
        assertThat(stillUnavailable.providerMutationPerformed()).isFalse();
    }

    @Test
    void guardedIdentityRealmApplyRequiresPersistedDryRunAndPolicySimulationEvidence() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        IdentityRealmDesiredState safeState = safeRealmState();

        var missingEvidence = service.applyIdentityRealm(new IdentityRealmApplyRequest(
                safeState,
                safeState,
                "realm-dry-run-never-persisted",
                null,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "attempt without stored evidence"), jwt("admin"));

        assertThat(missingEvidence.decision()).isEqualTo("blocked");
        assertThat(missingEvidence.blockedReasons()).contains(
                "fresh persisted dry-run evidence is required before identity realm apply",
                "effective policy simulation evidence ref is required before identity realm apply");
        assertThat(missingEvidence.applied()).isFalse();
        assertThat(missingEvidence.providerMutationPerformed()).isFalse();
    }

    @Test
    void guardedIdentityRealmApplyBlocksLiveProviderWhenReleaseEnabledButProviderUnavailable() {
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        IdentityRealmApplyProperties properties = new IdentityRealmApplyProperties();
        properties.setLiveApplyEnabled(true);
        properties.setProviderConfigured(false);
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher, properties);
        IdentityRealmDesiredState safeState = safeRealmState();

        var unavailable = service.applyIdentityRealm(applyRequest(service,
                safeState,
                safeState,
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "live provider unavailable",
                jwt("admin")), jwt("admin"));

        assertThat(unavailable.decision()).isEqualTo("blocked");
        assertThat(unavailable.executionMode()).isEqualTo("guarded-provider-apply-blocked-before-mutation");
        assertThat(unavailable.blockedReasons()).contains("Keycloak live apply adapter is enabled but provider runtime is not configured");
        assertThat(unavailable.applied()).isFalse();
        assertThat(unavailable.providerMutationPerformed()).isFalse();
    }


    @Test
    void providerSelectionPersistencePostureReflectsRepositoryBackingStore() {
        InMemoryProviderSelectionRepository selectionRepository = new InMemoryProviderSelectionRepository();
        selectionRepository.save(new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of()));
        WorkspaceCapabilityService workspaceCapabilityService = workspaceCapabilityService();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(), workspaceCapabilityService, selectionRepository);
        AdminControlPlaneService service = new AdminControlPlaneService(
                providerRegistry,
                workspaceCapabilityService,
                selectionRepository,
                new InMemoryOrganizationBootstrapRepository(),
                new InMemoryAuditEventPublisher(),
                Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC));

        var response = service.overview(jwt("admin"));

        assertThat(response.selectedProviderMappings())
                .singleElement()
                .satisfies(selection -> assertThat(selection.persistencePosture()).isEqualTo("in-memory-volatile"));
    }

    @Test
    void providerReplacementDryRunRecordsNoDriftEvidenceAgainstPersistedReadModels() {
        // ENTERPRISE_TARGET_PROVIDER_SWITCH_NO_DRIFT_FOUNDATION
        InMemoryAuditEventPublisher auditPublisher = new InMemoryAuditEventPublisher();
        InMemoryProviderSelectionRepository selectionRepository = new InMemoryProviderSelectionRepository();
        selectionRepository.save(new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                true,
                List.of("encrypted history requires manual review")));
        InMemoryMigrationRunEvidenceRepository migrationEvidenceRepository = new InMemoryMigrationRunEvidenceRepository();
        ProductProfileOverrideRepository profileRepository = profileOverrideRepository();
        profileRepository.saveForPrimaryIdentityKey(
                "issuer+subject:https://auth.example.invalid/realms/acme#admin-123",
                new ProductProfileOverride(
                        "Admin 123",
                        null,
                        "en",
                        "UTC",
                        Map.of("reducedMotion", "true"),
                        "workspace"));
        WorkspaceCapabilityService workspaceCapabilityService = workspaceCapabilityService();
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(), workspaceCapabilityService, selectionRepository);
        AdminControlPlaneService service = new AdminControlPlaneService(
                providerRegistry,
                workspaceCapabilityService,
                selectionRepository,
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                List.of(new KeycloakRealmDryRunProvider()),
                new InMemoryIdentityRealmEvidenceRepository(),
                List.of(new KeycloakRealmLiveApplyAdapter(new IdentityRealmApplyProperties())),
                new IdentityRealmApplyProperties(),
                Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC),
                profileRepository,
                migrationEvidenceRepository);

        var response = service.dryRunProviderReplacement(
                new com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunRequest(
                        "chat",
                        "synapse-homeserver",
                        "matrix-chat",
                        "recommended_self_hosted_default",
                        "secretref://weave/provider/matrix-chat",
                        "weave-chat-domain",
                        List.of("power-level parity requires manual review"),
                        true,
                        Map.of("window", "operator-reviewed"),
                        "support-safe dry-run"),
                jwt("admin"));

        assertThat(response.baselineSnapshot().persistedProviderKey()).isEqualTo("synapse-homeserver");
        assertThat(response.baselineSnapshot().persistedSelectionMatchesRequest()).isTrue();
        assertThat(response.baselineSnapshot().profileOverridePresent()).isTrue();
        assertThat(response.baselineSnapshot().profileOverridePersistencePosture()).isEqualTo("in-memory-test");
        assertThat(response.readModelComparison().northboundContractUnchanged()).isTrue();
        assertThat(response.readModelComparison().providerSemanticsLeakedToMembers()).isFalse();
        assertThat(response.readModelComparison().memberImpactStatesProviderNeutral()).isTrue();
        assertThat(response.readModelComparison().migrationEvidenceRecorded()).isTrue();
        assertThat(response.evidenceRefs()).anySatisfy(ref -> assertThat(ref).contains(":read-model-comparison"));
        assertThat(response.memberImpactStates()).containsOnly("available", "degraded", "unavailable", "coming_later");
        assertThat(response.memberImpactStates()).allSatisfy(state ->
                assertThat(response.baselineSnapshot().stableMemberImpactStates()).contains(state));
        assertThat(response.boundedProof().limitedApplyAllowed()).isFalse();
        assertThat(response.boundedProof().productionCutoverAllowed()).isFalse();
        assertThat(response.toString())
                .doesNotContain("secretref://", "Bearer ", "access_token", "rawProviderError");

        assertThat(migrationEvidenceRepository.findCurrent(response.dryRunId(), "chat", Instant.parse("2026-05-31T09:00:00Z")))
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.lifecycle()).isEqualTo("dry_run_completed");
                    assertThat(evidence.adminApproved()).isFalse();
                    assertThat(evidence.identityMappingComplete()).isTrue();
                    assertThat(evidence.auditSinkAvailable()).isTrue();
                    assertThat(evidence.artifactRefs()).containsKeys(
                            "baselineSnapshotRef",
                            "readModelComparisonRef",
                            "dryRunReportRef",
                            "providerMappingRef",
                            "crossDomainImpactReportRef");
                    assertThat(evidence.artifactRefs()).doesNotContainKey("adminApprovalRef");
                    assertThat(evidence.providerDiagnostics()).contains(
                            "support-safe provider replacement dry-run evidence",
                            "provider-selection-posture:in-memory-volatile",
                            "profile-override-posture:in-memory-test");
                });
        assertThat(auditPublisher.events()).singleElement().satisfies(event -> {
            assertThat(event.action()).isEqualTo(AuditAction.PROVIDER_REPLACEMENT_DRY_RUN);
            assertThat(event.payload()).containsKeys("migrationEvidenceRef", "baselineComparisonRef");
            assertThat(event.payload()).containsEntry("secretRef", "[redacted]");
        });
    }

    @Test
    void overviewIncludesSprint16SuiteGoLiveAndWeaverProjectionContracts() throws Exception {
        WorkspaceCapabilityService workspaceCapabilityService = workspaceCapabilityService();
        InMemoryProviderSelectionRepository selectionRepository = new InMemoryProviderSelectionRepository();
        selectionRepository.save(new ProviderSelection(
                "chat",
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of()));
        ProviderRegistry providerRegistry = new ProviderRegistry(List.of(), workspaceCapabilityService, selectionRepository);
        AdminControlPlaneService service = new AdminControlPlaneService(
                providerRegistry,
                workspaceCapabilityService,
                selectionRepository,
                new InMemoryOrganizationBootstrapRepository(),
                new InMemoryAuditEventPublisher(),
                Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC));

        var response = service.overview(jwt("admin"));

        assertThat(response.suiteDomainReadiness()).extracting(domain -> domain.domain())
                .containsExactly("files-docs", "boards-tasks", "calendar-meetings");
        assertThat(response.suiteDomainReadiness()).allSatisfy(domain -> {
            assertThat(domain.backendOwnedFacade()).isTrue();
            assertThat(domain.providerMappingOwnedByServer()).isTrue();
            assertThat(domain.rawProviderConfigExposedToMembers()).isFalse();
            assertThat(domain.supportSafeErrors()).contains("raw-provider-bodies-redacted");
        });
        assertThat(response.goLiveReadiness().supportSafe()).isTrue();
        assertThat(response.goLiveReadiness().normalMembersMayAccessSetupControls()).isFalse();
        assertThat(response.goLiveReadiness().releaseClaimControl().claimState())
                .isEqualTo("admin-action-required");
        assertThat(response.goLiveReadiness().releaseClaimControl().pinnedSpecCorpusRef())
                .contains("specs/weave-specs.lock.json#24c746c674da7d98e5c6abc1f1abac033a8774f2");
        assertThat(response.goLiveReadiness().releaseClaimControl().accessibilityEvidenceRef())
                .contains("docs/evidence/accessibility/sprint-18-manual-at-blocker.md#591");
        assertThat(response.goLiveReadiness().releaseClaimControl().unresolvedVetoes())
                .contains("#591-manual-assistive-technology-signoff-open", "release-owner-rc-decision-required");
        assertThat(response.goLiveReadiness().releaseClaimControl().gates()).extracting(gate -> gate.key())
                .contains("pinned-spec-corpus", "sprint-18-manual-at-signoff", "conformance-gates", "accessibility-evidence", "release-notes-input");
        assertThat(response.goLiveReadiness().releaseClaimControl().gates()).anySatisfy(gate -> {
            assertThat(gate.key()).isEqualTo("sprint-18-manual-at-signoff");
            assertThat(gate.blocksReleaseClaim()).isTrue();
            assertThat(gate.evidenceRefs()).contains("https://github.com/masssi164/weave/issues/591");
            assertThat(gate.nextAction()).contains("Sprint 19 dogfood work may proceed");
        });
        assertThat(response.goLiveReadiness().releaseClaimControl().gates()).anySatisfy(gate -> {
            assertThat(gate.key()).isEqualTo("conformance-gates");
            assertThat(gate.blocksReleaseClaim()).isTrue();
        });
        assertThat(response.adminApiRoutes()).containsEntry(
                "agentRuntimes", "/api/admin/agent-runtimes/{personRef}");
        assertThat(response.mcpServerBindings()).singleElement().satisfies(binding -> {
            assertThat(binding.serverKey()).isEqualTo("weave-domain-tools");
            assertThat(binding.transport()).isEqualTo("streamable-http");
            assertThat(binding.enabled()).isFalse();
            assertThat(binding.supportSafe()).isTrue();
            assertThat(binding.rawEndpointExposed()).isFalse();
            assertThat(binding.rawServerConfigExposed()).isFalse();
            assertThat(binding.secretValuesExposed()).isFalse();
            assertThat(binding.allowedTools()).contains("admin.get_readiness", "weaver.get_runtime_profile_projection", "calendar.search_events", "boards.comment");
            assertThat(binding.authRef()).startsWith("credentialref://");
        });
        assertThat(new ObjectMapper().findAndRegisterModules().writeValueAsString(response))
                .doesNotContain("openclaw.json", "Bearer ", "access_token", "rawProviderPayload", "rawMcpServerConfig");
    }

    @Test
    void checkedInApplyFixtureIsSupportSafeDecisionOnlyEvidence() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        try (InputStream input = getClass().getResourceAsStream("/identity-realm-apply/guarded-safe-accepted.json")) {
            assertThat(input).isNotNull();
            JsonNode fixture = objectMapper.readTree(input);

            assertThat(fixture.path("supportSafe").asBoolean()).isTrue();
            assertThat(fixture.path("decision").asText()).isEqualTo("accepted");
            assertThat(fixture.path("executionMode").asText()).isEqualTo("guarded-provider-live-apply-disabled");
            assertThat(fixture.path("applied").asBoolean()).isFalse();
            assertThat(fixture.path("providerMutationPerformed").asBoolean()).isFalse();
            assertThat(objectMapper.writeValueAsString(fixture))
                    .doesNotContain("@", "client_secret", "Authorization", "Bearer ", "access_token", "secretref://", "rollbackEvidenceRef", "issuer+subject:");
        }
    }

    private AdminControlPlaneService adminControlPlaneService(AuditEventPublisher auditPublisher) {
        return adminControlPlaneService(auditPublisher, new IdentityRealmApplyProperties());
    }

    private AdminControlPlaneService adminControlPlaneService(
            AuditEventPublisher auditPublisher,
            IdentityRealmApplyProperties properties) {
        return new AdminControlPlaneService(
                mock(ProviderRegistry.class),
                workspaceCapabilityService(),
                new InMemoryProviderSelectionRepository(),
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                List.of(new KeycloakRealmDryRunProvider()),
                new InMemoryIdentityRealmEvidenceRepository(),
                List.of(new KeycloakRealmLiveApplyAdapter(properties)),
                properties,
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC));
    }

    private DriverManagerDataSource migratedDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }

    private WorkspaceCapabilityService workspaceCapabilityService() {
        OAuth2ResourceServerProperties properties = new OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.weave.test/realms/weave");
        return new WorkspaceCapabilityService(
                properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new WorkspaceCapabilityProperties(null, null, null, null, null, null));
    }

    private ProductProfileOverrideRepository profileOverrideRepository() {
        Map<String, ProductProfileOverride> profiles = new java.util.LinkedHashMap<>();
        return new ProductProfileOverrideRepository() {
            @Override
            public ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey) {
                return profiles.get(primaryIdentityKey);
            }

            @Override
            public ProductProfileOverride saveForPrimaryIdentityKey(String primaryIdentityKey, ProductProfileOverride profile) {
                profiles.put(primaryIdentityKey, profile);
                return profile;
            }

            @Override
            public String persistencePosture() {
                return "in-memory-test";
            }
        };
    }

    private IdentityRealmApplyRequest applyRequest(
            AdminControlPlaneService service,
            IdentityRealmDesiredState currentState,
            IdentityRealmDesiredState desiredState,
            String confirmationPhrase,
            boolean approveRisky,
            boolean approveDestructive,
            List<String> retainedAdminPrimaryIdentityKeys,
            String rollbackEvidenceRef,
            String reason,
            Jwt jwt) {
        var dryRun = service.dryRunIdentityRealm(new IdentityRealmDryRunRequest(currentState, desiredState, reason), jwt);
        var simulation = service.simulateEffectivePolicy(new EffectivePolicySimulationRequest(
                "issuer+subject:https://auth.example.invalid/realms/weave#member-123",
                "weave-dogfood",
                List.of("member"),
                List.of("weave-board-editors"),
                List.of("chat.read", "boards.update_task"),
                "support-safe policy simulation before realm apply"), jwt);
        return new IdentityRealmApplyRequest(
                currentState,
                desiredState,
                dryRun.dryRunId(),
                simulation.auditRefs().get(0),
                confirmationPhrase,
                approveRisky,
                approveDestructive,
                retainedAdminPrimaryIdentityKeys,
                rollbackEvidenceRef,
                reason);
    }


    private IdentityRealmDesiredState safeRealmState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.test/callback"),
                        List.of("owner", "admin", "member"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member"),
                List.of("weave-board-editors"),
                List.of("openid", "profile", "email", "weave:workspace"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.test/callback"),
                List.of(new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("weave-board-editors"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("operator"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("issuer+subject:https://auth.example.invalid/realms/weave#admin-123", "last-admin recovery", true, List.of("owner"))),
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
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
                        List.of("https://weave.test/callback", "http://localhost:8080/*"),
                        List.of("owner", "admin", "member"),
                        List.of("openid", "profile", "email"))),
                List.of("owner", "admin", "member"),
                List.of("weave-board-editors"),
                List.of("openid", "profile", "email", "weave:workspace"),
                List.of(new IdentityRealmDesiredState.ClaimMapper("tenant", "weave_tenant", "organizationId", true)),
                List.of("https://weave.test/callback", "http://localhost:8080/*"),
                List.of(new IdentityRealmDesiredState.FeatureMapping("boards", List.of("member"), List.of("weave-board-editors"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.ServiceAccount("subject:service:backend", List.of("operator"), List.of("openid"))),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("issuer+subject:https://auth.example.invalid/realms/weave#admin-123", "last-admin recovery", true, List.of("owner"))),
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
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
                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of(role))))
                .claim("groups", List.of())
                .build();
    }
}
