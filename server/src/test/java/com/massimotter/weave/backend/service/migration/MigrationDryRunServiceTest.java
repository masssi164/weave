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
