package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.service.interop.IdempotencyKeyService;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationDryRunServiceTest {

    @Test
    void dryRunIncludesSupportSafeChatAndFilesMappingEvidence() {
        var repository = new InMemoryMigrationRunEvidenceRepository();
        var service = new MigrationDryRunService(new IdempotencyKeyService(), repository);

        var response = service.dryRun(new MigrationDryRunRequest(
                "slack",
                new MigrationDryRunRequest.SourceInventory(
                        1,
                        2,
                        5,
                        13,
                        200,
                        List.of("channels:read", "users:read", "files:read"))));

        assertThat(response.supportSafe()).isTrue();
        assertThat(response.providerDiagnosticsRedacted()).isTrue();
        assertThat(response.replaySafe()).isTrue();
        assertThat(response.domainMappings()).extracting("domain").containsExactly("files", "calendar", "boards", "chat");
        assertThat(response.continuityReports()).filteredOn(report -> report.domain().equals("boards"))
                .singleElement()
                .satisfies(report -> {
                    assertThat(report.canonicalObjectCounts()).containsEntry("Board", 1).containsEntry("Task", 15).containsEntry("Watcher", 5);
                    assertThat(report.stableIdStrategy()).startsWith("weave:boards").contains("sha256-normalized-title-status-owner");
                    assertThat(report.provenanceRefs()).allSatisfy(ref -> assertThat(ref).doesNotContain("https://", "token", "Bearer"));
                    assertThat(report.lossyFields()).isNotEmpty();
                    assertThat(report.permissionImpact()).isNotEmpty();
                    assertThat(report.conflicts()).isNotEmpty();
                    assertThat(report.unsupportedObjects()).contains("board automations", "provider-only custom fields");
                    assertThat(report.abortRollbackPosture()).contains("dry-run only", "rollback archive", "restore smoke");
                    assertThat(report.accountedForNoDataLoss()).isTrue();
                });
        assertThat(response.domainMappings()).extracting("mappingClass")
                .contains("portable", "lossy", "manual_review", "archive_only");
        assertThat(response.domainMappings())
                .anySatisfy(mapping -> assertThat(mapping.weaveDomainObject()).contains("weave:files"))
                .anySatisfy(mapping -> assertThat(mapping.weaveDomainObject()).contains("weave:calendar"))
                .anySatisfy(mapping -> assertThat(mapping.weaveDomainObject()).contains("weave:boards"))
                .anySatisfy(mapping -> assertThat(mapping.weaveDomainObject()).contains("weave:chat"));
        assertThat(response.toString())
                .doesNotContain("https://")
                .doesNotContain("Authorization")
                .doesNotContain("Bearer")
                .doesNotContain("token");
        assertThat(repository.findCurrent(response.jobId(), "files", java.time.Instant.now())).isPresent();
        assertThat(repository.findCurrent(response.jobId(), "calendar", java.time.Instant.now())).isPresent();
        assertThat(repository.findCurrent(response.jobId(), "boards", java.time.Instant.now())).isPresent();
        assertThat(repository.findCurrent(response.jobId(), "chat", java.time.Instant.now())).isPresent();
    }

    @Test
    void matrixChatDryRunEmitsBlockedSupportSafeEvidence() {
        var repository = new InMemoryMigrationRunEvidenceRepository();
        var service = new MigrationDryRunService(new IdempotencyKeyService(), repository);

        var response = service.dryRun(new MigrationDryRunRequest(
                "matrix-synapse",
                new MigrationDryRunRequest.SourceInventory(
                        2,
                        4,
                        8,
                        6,
                        120,
                        List.of("rooms:read", "members:read", "messages:read", "media:read"))));

        assertThat(response.sourceProvider()).isEqualTo("matrix-synapse");
        assertThat(response.consentRequirements().missingScopes()).isEmpty();
        assertThat(response.cutoverGates()).anySatisfy(gate -> assertThat(gate).contains("Sprint 15 Matrix Chat dry-run evidence is review-only"));
        assertThat(response.cutoverGates()).anySatisfy(gate -> assertThat(gate).contains("Encrypted-room history is unsupported"));
        assertThat(response.domainMappings()).filteredOn(mapping -> mapping.domain().equals("chat"))
                .singleElement()
                .satisfies(mapping -> {
                    assertThat(mapping.sourceObject()).contains("matrix-synapse:channels/messages/memberships/e2ee-state");
                    assertThat(mapping.mappingClass()).isEqualTo("portable");
                    assertThat(mapping.lossyFields()).anySatisfy(field -> assertThat(field).contains("encrypted/redacted history"));
                    assertThat(mapping.assumptions()).anySatisfy(assumption -> assertThat(assumption).contains("raw media URLs are redacted"));
                });
        assertThat(repository.findCurrent(response.jobId(), "chat", java.time.Instant.now()))
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.adminApproved()).isFalse();
                    assertThat(evidence.artifactRefs()).containsKeys("dryRunReportRef", "rollbackArchiveRef", "memberImpactPreviewRef");
                    assertThat(evidence.providerDiagnostics()).containsExactly("support-safe migration dry-run evidence");
                });
        assertThat(response.toString().toLowerCase(Locale.ROOT))
                .doesNotContain("mxc://", "access_token", "homeserverurl", "https://matrix");
    }

    @Test
    void unsafeUnknownSourceProviderIsMappedToSupportSafeGenericKey() {
        var repository = new InMemoryMigrationRunEvidenceRepository();
        var service = new MigrationDryRunService(new IdempotencyKeyService(), repository);
        String unsafeProvider = "https://provider.example/export?access_token=secret-ish-token";

        var response = service.dryRun(new MigrationDryRunRequest(
                unsafeProvider,
                new MigrationDryRunRequest.SourceInventory(
                        1,
                        1,
                        2,
                        3,
                        4,
                        List.of("inventory:read"))));

        assertThat(response.sourceProvider()).isEqualTo("external-provider");
        assertThat(response.consentRequirements().missingScopes()).isEmpty();
        assertThat(response.continuityReports()).allSatisfy(report -> {
            assertThat(report.provenanceRefs()).contains("provider:external-provider:boards-export-snapshot");
            assertThat(report.provenanceRefs()).allSatisfy(ref -> assertThat(ref)
                    .doesNotContain("https://", "provider.example", "access_token", "secret-ish-token"));
        });
        assertThat(response.domainMappings()).allSatisfy(mapping ->
                assertThat(mapping.sourceObject())
                        .startsWith("external-provider:")
                        .doesNotContain("https://", "provider.example", "access_token", "secret-ish-token"));
        assertThat(response.toString())
                .doesNotContain(unsafeProvider)
                .doesNotContain("https://", "provider.example", "access_token", "secret-ish-token");
    }

    @Test
    void providerNormalizationIsLocaleStable() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            var service = new MigrationDryRunService(new IdempotencyKeyService(), new InMemoryMigrationRunEvidenceRepository());

            var response = service.dryRun(new MigrationDryRunRequest(
                    "TEAMS",
                    new MigrationDryRunRequest.SourceInventory(
                            1,
                            1,
                            1,
                            0,
                            0,
                            List.of("Channel.ReadBasic.All", "User.Read.All", "Files.Read.All"))));

            assertThat(response.sourceProvider()).isEqualTo("teams");
            assertThat(response.consentRequirements().missingScopes()).isEmpty();
            assertThat(response.domainMappings()).allSatisfy(mapping ->
                    assertThat(mapping.sourceObject()).startsWith("teams:"));
        } finally {
            Locale.setDefault(previous);
        }
    }
}
