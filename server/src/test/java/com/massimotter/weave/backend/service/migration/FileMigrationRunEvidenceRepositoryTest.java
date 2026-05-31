package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void failedFilePersistDoesNotLeaveObservableInMemoryEvidence() throws IOException {
        Path storagePath = tempDir.resolve("migration-run-evidence.json");
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        ObjectWriter writer = mock(ObjectWriter.class);
        when(objectMapper.writerWithDefaultPrettyPrinter()).thenReturn(writer);
        doThrow(new IOException("write failed")).when(writer).writeValue(any(File.class), any());
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new FileMigrationRunEvidenceRepository(objectMapper, storagePath);
        MigrationRunEvidence evidence = new MigrationRunEvidence(
                "migration-chat-001",
                "chat",
                "approved",
                Map.of("Conversation", 2),
                List.of("sha256:1111111111111111111111111111111111111111111111111111111111111111"),
                List.of("audit:migration.dry_run:001"),
                Map.of("dryRunReportRef", "dry-run:chat:001"),
                List.of("support-safe migration evidence"),
                true,
                true,
                true,
                now,
                now.plusSeconds(3600));

        assertThatThrownBy(() -> repository.save(evidence))
                .isInstanceOf(MigrationRunEvidenceStoreException.class)
                .hasMessageContaining("Failed to persist migration run evidence");

        assertThat(repository.findCurrent("migration-chat-001", "chat", now.plusSeconds(30))).isEmpty();
    }
}
