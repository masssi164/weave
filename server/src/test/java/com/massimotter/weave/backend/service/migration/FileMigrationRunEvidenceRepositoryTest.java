package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileMigrationRunEvidenceRepositoryTest {

    @TempDir
    Path tempDir;

    @Test
    void migrationRunEvidenceSurvivesRepositoryRestartAndExpiresFailClosed() {
        Path storagePath = tempDir.resolve("migration-run-evidence.json");
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new FileMigrationRunEvidenceRepository(objectMapper, storagePath);

        repository.save(new MigrationRunEvidence(
                "migration-chat-001",
                "chat",
                "approved",
                Map.of("Conversation", 2, "Message", 10),
                List.of("sha256:1111111111111111111111111111111111111111111111111111111111111111"),
                List.of("audit:migration.dry_run:001"),
                Map.of("dryRunReportRef", "dry-run:chat:001", "adminApprovalRef", "approval:chat:001"),
                List.of("support-safe migration evidence"),
                true,
                true,
                true,
                now,
                now.plusSeconds(3600)));

        var restartedRepository = new FileMigrationRunEvidenceRepository(objectMapper, storagePath);

        assertThat(restartedRepository.findCurrent("migration-chat-001", "chat", now.plusSeconds(30)))
                .isPresent()
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.lifecycle()).isEqualTo("approved");
                    assertThat(evidence.objectCounts()).containsEntry("Message", 10);
                    assertThat(evidence.auditSinkAvailable()).isTrue();
                    assertThat(evidence.adminApproved()).isTrue();
                });
        assertThat(restartedRepository.findCurrent("migration-chat-001", "chat", now.plusSeconds(7200))).isEmpty();
        assertThat(storagePath).exists();
    }
}
