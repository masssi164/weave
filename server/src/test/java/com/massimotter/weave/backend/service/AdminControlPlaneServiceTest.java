package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.audit.JpaAuditEventPublisher;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
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
import org.junit.jupiter.api.Test;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
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
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC),
                mock(ProductProfileOverrideRepository.class),
                new InMemoryMigrationRunEvidenceRepository());
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
    void jpaAuditPublisherFeedsAdminAuditReadback() {
        DriverManagerDataSource dataSource = migratedDataSource();
        AuditEventJpaRepository repository =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, AuditEventJpaRepository.class);
        JpaAuditEventPublisher auditPublisher =
                new JpaAuditEventPublisher(
                        repository, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
        AdminControlPlaneService service = adminControlPlaneService(auditPublisher);
        CapabilityWhitelistUpdateRequest request = new CapabilityWhitelistUpdateRequest(
                "workspace-admin",
                List.of("admin.policy.edit", "admin.provider.configure"),
                "prove jpa audit readback");

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
                List.of("/members"),
                List.of("chat.send", "boards.update_task", "admin.provider.configure", "agent-runtime.entitled"),
                "simulate before #233 dry-run/apply with Bearer operator-token and secretref://weave/provider/keycloak");

        var adminResponse = service.simulateEffectivePolicy(request, jwt("admin"));

        assertThat(adminResponse.supportSafe()).isTrue();
        assertThat(adminResponse.unknownInputsFailClosed()).isFalse();
        assertThat(adminResponse.agentRuntimeEntitlementRequired()).isTrue();
        assertThat(adminResponse.grantedCapabilities()).containsExactly("chat.send");
        assertThat(adminResponse.capabilityStates()).extracting(state -> state.state())
                .containsOnly("ready", "disabled", "policy-blocked");
        assertThat(adminResponse.capabilityStates()).filteredOn(state -> state.capability().equals("agent-runtime.entitled"))
                .singleElement()
                .satisfies(state -> {
                    assertThat(state.state()).isEqualTo("disabled");
                    assertThat(state.reasonCode()).isEqualTo("agent-runtime-entitlement-required");
                });
        assertThat(adminResponse.capabilityStates())
                .filteredOn(state -> state.capability().equals("boards.update_task"))
                .singleElement()
                .satisfies(state -> assertThat(state.state()).isEqualTo("policy-blocked"));
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
        assertThat(tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(auditPublisher.events()))
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
        ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        try (InputStream input = getClass().getResourceAsStream("/effective-policy-simulation/admin-operator-preview.json")) {
            assertThat(input).isNotNull();
            JsonNode fixture = objectMapper.readTree(input);

            assertThat(fixture.path("supportSafe").asBoolean()).isTrue();
            assertThat(fixture.path("agentRuntimeEntitlementRequired").asBoolean()).isTrue();
            assertThat(fixture.path("unknownInputsFailClosed").asBoolean()).isFalse();
            assertThat(fixture.path("subject").asString()).startsWith("issuer+subject:");
            assertThat(fixture.path("capabilityStates")).allSatisfy(state ->
                    assertThat(state.path("state").asString()).isIn("ready", "disabled", "degraded", "policy-blocked"));
            assertThat(objectMapper.writeValueAsString(fixture))
                    .doesNotContain("@", "client_secret", "Authorization", "Bearer ", "access_token", "secretref://", "keycloak-realm");
        }
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
                Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC),
                mock(ProductProfileOverrideRepository.class),
                new InMemoryMigrationRunEvidenceRepository());

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
                Clock.fixed(Instant.parse("2026-05-31T08:00:00Z"), ZoneOffset.UTC),
                mock(ProductProfileOverrideRepository.class),
                new InMemoryMigrationRunEvidenceRepository());

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
        assertThat(tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build().writeValueAsString(response))
                .doesNotContain("openclaw.json", "Bearer ", "access_token", "rawProviderPayload", "rawMcpServerConfig");
    }

    private AdminControlPlaneService adminControlPlaneService(AuditEventPublisher auditPublisher) {
        return new AdminControlPlaneService(
                mock(ProviderRegistry.class),
                workspaceCapabilityService(),
                new InMemoryProviderSelectionRepository(),
                new InMemoryOrganizationBootstrapRepository(),
                auditPublisher,
                Clock.fixed(Instant.parse("2026-05-27T01:03:39Z"), ZoneOffset.UTC),
                mock(ProductProfileOverrideRepository.class),
                new InMemoryMigrationRunEvidenceRepository());
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .entityFirstDataSource("admin-control-plane");
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

    private Jwt jwt(String role) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(role + "-123")
                .issuer("https://auth.example.invalid/realms/acme")
                .claim("weave_tenant_id", "weave-dogfood")
                .claim("organization", HumanJwtTestSupport.organizationWithRole(role))
                .build();
    }
}
