package com.massimotter.weave.backend.domainregistry;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
            "weaver");

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
        assertThat(registry.domains()).extracting(CanonicalDomainRegistryEntryResponse::key)
                .containsExactlyElementsOf(CANONICAL_DOMAINS);
        assertThat(registry.compatibilityAliases())
                .containsEntry("identity-idm", "identity")
                .containsEntry("boards-tasks", "boards")
                .containsEntry("meetings-calls", "calls")
                .containsEntry("documents-collaboration", "documents");
        assertThat(registry.providerNamesInMemberContractsAllowed()).isFalse();

        registry.domains().forEach(domain -> {
            assertThat(domain.version()).isEqualTo(1);
            assertThat(domain.canonicalObjects()).isNotEmpty();
            assertThat(domain.capabilityKeys()).contains(
                    domain.key() + ".read",
                    domain.key() + ".export",
                    domain.key() + ".dryRun",
                    domain.key() + ".apply",
                    domain.key() + ".adminConfigure");
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
        });
    }

    @Test
    void machineReadableRegistryResourceMatchesRuntimeRegistry() throws Exception {
        JsonNode registry = readContract("canonical-domain-registry.v1.json");

        assertThat(registry.path("registry_version").asText()).isEqualTo(CanonicalDomainRegistry.REGISTRY_VERSION);
        assertThat(registry.path("domains")).hasSize(CANONICAL_DOMAINS.size());
        assertThat(registry.path("domains").findValuesAsText("key")).containsExactlyElementsOf(CANONICAL_DOMAINS);
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

    private JsonNode readContract(String name) throws Exception {
        return OBJECT_MAPPER.readTree(Files.readString(Path.of("src/main/resources/contracts", name)));
    }
}
