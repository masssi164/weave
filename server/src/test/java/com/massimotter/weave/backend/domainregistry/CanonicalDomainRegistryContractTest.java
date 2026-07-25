package com.massimotter.weave.backend.domainregistry;

import static org.assertj.core.api.Assertions.assertThat;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CanonicalDomainRegistryContractTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> CANONICAL_DOMAINS = List.of(
            "identity",
            "people",
            "spaces",
            "chat",
            "files",
            "documents",
            "calendar",
            "boards",
            "calls",
            "decisions",
            "notifications",
            "health",
            "agent-runtime-control");

    @Test
    void registryDefinesCanonicalDomainsStatesAliasesAndPortabilityMetadata() {
        CanonicalDomainRegistryResponse registry = CanonicalDomainRegistry.snapshot();

        assertThat(registry.registryVersion()).isEqualTo("canonical-domain-registry-v1");
        assertThat(registry.memberStates()).containsExactly(
                "available",
                "disabled_by_policy",
                "not_configured",
                "degraded",
                "unavailable",
                "coming_later");
        assertThat(registry.adminStates()).containsExactly(
                "provider_not_configured",
                "secret_missing",
                "ready",
                "degraded",
                "dry_run_required",
                "lossy_mapping_pending",
                "apply_blocked",
                "migration_ready");
        assertThat(registry.lossClasses()).containsExactly(
                "lossless_canonical",
                "lossless_extension",
                "archive_only",
                "lossy_with_report",
                "blocked_nonportable",
                "provider_unexportable");
        assertThat(registry.providerRealityLevels()).containsExactly(
                "contract_only",
                "configured",
                "live_read",
                "live_write",
                "migration_dry_run",
                "migration_apply_ready",
                "rollback_ready",
                "release_ready");
        assertThat(registry.domains()).extracting(CanonicalDomainRegistryEntryResponse::key)
                .containsExactlyElementsOf(CANONICAL_DOMAINS);
        assertThat(registry.compatibilityAliases())
                .containsEntry("identity-idm", "identity")
                .containsEntry("boards-tasks", "boards")
                .containsEntry("meetings-calls", "calls")
                .containsEntry("documents-collaboration", "documents");
        assertThat(registry.providerNamesInMemberContractsAllowed()).isFalse();
        assertThat(providerRealityLevels(registry, "calendar"))
                .containsEntry("weave-calendar", "contract_only")
                .doesNotContainKeys("workspace-calendar", "team-channel-calendar");
        assertThat(providerRealityLevels(registry, "documents"))
                .containsEntry("onlyoffice", "contract_only")
                .containsEntry("collabora", "contract_only")
                .doesNotContainKeys("onlyoffice-community", "collabora-code");

        registry.domains().forEach(domain -> {
            assertThat(domain.version()).isEqualTo(1);
            assertThat(domain.canonicalObjects()).isNotEmpty();
            if (domain.key().equals("agent-runtime-control")) {
                assertThat(domain.capabilityKeys()).contains(
                        "agent-runtime.entitled",
                        "agent-runtime.profile.read",
                        "agent-runtime.lifecycle.write",
                        "agent-runtime.wake",
                        "agent-runtime.approval.attest",
                        "agent-runtime.admin");
            } else {
                assertThat(domain.capabilityKeys()).contains(
                        domain.key() + ".read",
                        domain.key() + ".export",
                        domain.key() + ".dryRun",
                        domain.key() + ".apply",
                        domain.key() + ".adminConfigure");
            }
            assertThat(domain.memberStates()).containsExactlyElementsOf(registry.memberStates());
            assertThat(domain.adminStates()).containsExactlyElementsOf(registry.adminStates());
            assertThat(domain.sourceOfTruthModes()).contains("weave_owned", "selected_provider_owned", "hybrid_composite");
            assertThat(domain.portabilityRequirements()).contains(
                    "provider_mapping_refs_required",
                    "loss_classification_required",
                    "dry_run_required_before_apply",
                    "support_safe_migration_evidence_required",
                    "audit_ref_required");
            assertThat(domain.adapterManifestRequirements()).contains(
                    "supported_domain_key",
                    "canonical_object_coverage",
                    "secret_ref_only",
                    "support_safe_diagnostics");
            assertThat(domain.providerRealityLevelByCandidate().values())
                    .allMatch(registry.providerRealityLevels()::contains);
            assertThat(domain.providerRealityLevelByCandidate().values()).isNotEmpty();
        });
    }

    @Test
    void sprintNineFoundationalDomainsExposeProductReadyCanonicalObjects() {
        CanonicalDomainRegistryResponse registry = CanonicalDomainRegistry.snapshot();

        assertThat(objects(registry, "spaces")).contains("Space", "SpaceMembership", "RoleBinding", "LinkedChannel", "LinkedBoard", "LinkedCalendar", "LinkedFileRoot", "DecisionLedgerRef");
        assertThat(objects(registry, "people")).contains("Person", "Membership", "Profile", "ContactMethod", "Team", "Guest", "ServiceAccountRef");
        assertThat(objects(registry, "notifications")).contains("Notification", "Preference", "Channel", "Digest", "DeliveryAttempt", "Subscription");
        assertThat(objects(registry, "decisions")).contains("Decision", "Proposal", "Approval", "Rationale", "DecisionLink", "EvidenceRef", "Supersession");
        assertThat(objects(registry, "health")).contains("ReadinessCard", "Diagnostic", "SupportBundle", "BackupJob", "RestoreDrill", "EvidenceItem", "AuditRef");
        assertThat(objects(registry, "chat")).contains("WeaveSpace", "WeaveConversation", "WeaveMessage", "WeaveThread", "WeaveReaction", "WeaveAttachment", "WeaveMembership", "WeaveHistoryPolicy", "ProviderRef", "MigrationReceipt", "RollbackReceipt", "LossyFieldReport");
        assertThat(objects(registry, "files")).contains("WeaveDrive", "WeaveFolder", "WeaveFile", "WeaveVersion", "WeaveShare", "WeavePermission", "WeaveLock", "WeaveQuota", "ProviderRef");
        assertThat(objects(registry, "documents")).contains("Document", "EditSession", "Comment", "Suggestion", "CoauthorPresence", "Version", "Export");
        assertThat(objects(registry, "calendar")).contains("WeaveCalendar", "WeaveEvent", "WeaveRecurrence", "WeaveAttendee", "WeaveResource", "WeaveAvailability", "ProviderRef");
        assertThat(objects(registry, "agent-runtime-control")).contains(
                "RuntimeEntitlementRef", "RuntimeProfile", "ApprovalChallenge", "RuntimeCell",
                "WorkspaceRevision", "RuntimeRevocation", "RuntimeAuditCorrelation");
        assertThat(objects(registry, "boards")).contains("Board", "List", "Task", "Status", "Assignee", "Comment", "AttachmentRef", "Dependency", "CustomField");
        assertThat(objects(registry, "calls")).contains(
                "Meeting", "MatrixRtcSlot", "MatrixRtcMember", "DeviceBinding", "MediaSession",
                "RtcAuthorization", "Recording", "Caption", "ConsentRecord");
    }

    @Test
    void machineReadableRegistryResourceMatchesRuntimeRegistry() throws Exception {
        JsonNode registry = readContract("canonical-domain-registry.v1.json");

        assertThat(registry.path("registry_version").asText()).isEqualTo(CanonicalDomainRegistry.REGISTRY_VERSION);
        assertThat(registry.path("domains")).hasSize(CANONICAL_DOMAINS.size());
        assertThat(registry.path("domains").findValuesAsString("key")).containsExactlyElementsOf(CANONICAL_DOMAINS);
        assertThat(registry.path("compatibility_aliases").path("boards-tasks").asText()).isEqualTo("boards");
        assertThat(registry.path("compatibility_aliases").path("meetings-calls").asText()).isEqualTo("calls");
        assertThat(registry.toString()).doesNotContain("secretref://", "Bearer ", "access_token", "client-secret");
    }

    @Test
    void portabilityContractSchemasCarryNoUnaccountedDataLossAndApplySafetyRules() throws Exception {
        Set<String> schemas = Set.of(
                "provider-adapter-manifest.schema.json",
                "provider-mapping.schema.json",
                "export-manifest.schema.json",
                "import-manifest.schema.json",
                "lossy-mapping-report.schema.json",
                "conflict-report.schema.json",
                "migration-run.schema.json");

        for (String schemaName : schemas) {
            JsonNode schema = readContract(schemaName);
            assertThat(schema.path("$schema").asText()).isEqualTo("https://json-schema.org/draft/2020-12/schema");
            assertThat(schema.toString()).contains("support_safe_diagnostics");
            assertThat(schema.toString()).contains("secrets_returned");
            assertThat(schema.toString()).contains("raw_provider_payloads_returned");
            assertThat(schema.toString()).contains("raw_provider_errors_returned");
        }

        JsonNode migrationRun = readContract("migration-run.schema.json");
        assertThat(migrationRun.path("properties").path("lifecycle").path("enum").toString())
                .contains("preflight_passed", "dry_run_completed", "approved", "applied", "verified", "rolled_back");
        assertThat(migrationRun.path("properties").path("dry_run_required_before_apply").path("const").asBoolean()).isTrue();
        assertThat(migrationRun.path("properties").path("apply_blockers").path("items").path("enum").toString())
                .contains("missing_dry_run", "incomplete_identity_mapping", "audit_sink_unavailable");

        JsonNode lossyReport = readContract("lossy-mapping-report.schema.json");
        assertThat(lossyReport.toString()).contains(
                "lossless_canonical",
                "lossless_extension",
                "archive_only",
                "lossy_with_report",
                "blocked_nonportable",
                "provider_unexportable");
    }

    private java.util.Map<String, String> providerRealityLevels(CanonicalDomainRegistryResponse registry, String domainKey) {
        return registry.domains().stream()
                .filter(domain -> domain.key().equals(domainKey))
                .findFirst()
                .orElseThrow()
                .providerRealityLevelByCandidate();
    }

    private List<String> objects(CanonicalDomainRegistryResponse registry, String domainKey) {
        return registry.domains().stream()
                .filter(domain -> domain.key().equals(domainKey))
                .findFirst()
                .orElseThrow()
                .canonicalObjects();
    }

    private JsonNode readContract(String name) throws Exception {
        return OBJECT_MAPPER.readTree(Files.readString(Path.of("src/main/resources/contracts", name)));
    }
}
