package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcMigrationRunEvidenceRepositoryTest {

    private static final String ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION =
            "ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION";

    @TempDir
    Path tempDir;

    @Test
    void relationalMigrationRunEvidenceSurvivesRepositoryRestartAndExpiresFailClosed() {
        assertThat(ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new JdbcMigrationRunEvidenceRepository(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());

        repository.save(evidence("migration-chat-001", "chat", now));

        var restartedRepository = new JdbcMigrationRunEvidenceRepository(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());

        assertThat(restartedRepository.findCurrent("migration-chat-001", "chat", now.plusSeconds(30)))
                .isPresent()
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.lifecycle()).isEqualTo("approved");
                    assertThat(evidence.objectCounts()).containsEntry("Message", 10);
                    assertThat(evidence.auditRefs()).contains("audit:migration.dry_run:001");
                    assertThat(evidence.artifactRefs()).containsEntry("dryRunReportRef", "dry-run:chat:001");
                    assertThat(evidence.auditSinkAvailable()).isTrue();
                    assertThat(evidence.adminApproved()).isTrue();
                });
        assertThat(restartedRepository.findCurrent("migration-chat-001", "chat", now.plusSeconds(7200))).isEmpty();
        assertThat(repository.persistencePosture()).isEqualTo("durable-relational-flyway");
    }

    @Test
    void relationalPathMatchesExistingFileRepositoryContractForMigrationEvidence() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        MigrationRunEvidence evidence = evidence("migration-chat-002", "chat", now);
        var fileRepository = new FileMigrationRunEvidenceRepository(
                objectMapper,
                tempDir.resolve("migration-run-evidence.json"));
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jdbcRepository = new JdbcMigrationRunEvidenceRepository(new JdbcTemplate(dataSource), objectMapper);

        fileRepository.save(evidence);
        jdbcRepository.save(evidence);

        assertThat(jdbcRepository.findCurrent("migration-chat-002", "chat", now.plusSeconds(30)))
                .isEqualTo(fileRepository.findCurrent("migration-chat-002", "chat", now.plusSeconds(30)));
    }

    @Test
    void relationalMigrationRunEvidenceCanBeReplacedByRunAndDomain() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new JdbcMigrationRunEvidenceRepository(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());

        repository.save(evidence("migration-chat-003", "chat", now));
        repository.save(new MigrationRunEvidence(
                "migration-chat-003",
                "chat",
                "verified",
                Map.of("Conversation", 2, "Message", 10),
                List.of("sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                List.of("audit:migration.verify:003"),
                Map.of("verificationReportRef", "verify:chat:003"),
                List.of("support-safe verification evidence"),
                true,
                true,
                true,
                now.plusSeconds(60),
                now.plusSeconds(3600)));

        assertThat(repository.findCurrent("migration-chat-003", "chat", now.plusSeconds(120)))
                .isPresent()
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.lifecycle()).isEqualTo("verified");
                    assertThat(evidence.artifactRefs()).containsEntry("verificationReportRef", "verify:chat:003");
                });
    }

    @Test
    void recordedAtIsRequiredBeforeJdbcPersistence() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcMigrationRunEvidenceRepository(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> repository.save(new MigrationRunEvidence(
                "migration-chat-missing-recorded-at",
                "chat",
                "approved",
                Map.of("Conversation", 2),
                List.of("sha256:4444444444444444444444444444444444444444444444444444444444444444"),
                List.of("audit:migration.dry_run:missing-recorded-at"),
                Map.of("dryRunReportRef", "dry-run:chat:missing-recorded-at"),
                List.of("support-safe migration evidence"),
                true,
                true,
                true,
                null,
                null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Migration run evidence recordedAt must not be null.");
    }

    @Test
    void jdbcStoreFailuresAreWrappedForSupportSafeCallers() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table weave_migration_run_evidence");
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new JdbcMigrationRunEvidenceRepository(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> repository.save(evidence("migration-chat-write-failure", "chat", now)))
                .isInstanceOf(MigrationRunEvidenceStoreException.class)
                .hasMessage("Failed to persist durable migration run evidence.")
                .hasMessageNotContaining("WEAVE_MIGRATION_RUN_EVIDENCE");
        assertThatThrownBy(() -> repository.findCurrent("migration-chat-write-failure", "chat", now))
                .isInstanceOf(MigrationRunEvidenceStoreException.class)
                .hasMessage("Failed to load durable migration run evidence.")
                .hasMessageNotContaining("WEAVE_MIGRATION_RUN_EVIDENCE");
    }

    @Test
    void missingRecordedAtFailsFastBeforeJdbcWrite() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcMigrationRunEvidenceRepository(
                new JdbcTemplate(dataSource),
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> repository.save(new MigrationRunEvidence(
                "migration-chat-missing-recorded-at",
                "chat",
                "approved",
                Map.of("Conversation", 2),
                List.of("sha256:4444444444444444444444444444444444444444444444444444444444444444"),
                List.of("audit:migration.dry_run:missing-recorded-at"),
                Map.of("dryRunReportRef", "dry-run:chat:missing-recorded-at"),
                List.of("support-safe migration evidence"),
                true,
                true,
                true,
                null,
                Instant.parse("2026-05-31T09:00:00Z"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Migration run evidence recordedAt must not be null.");
    }

    @Test
    void jdbcTransactionFailuresAreWrappedForSupportSafeCallers() throws Exception {
        DriverManagerDataSource dataSource = mock(DriverManagerDataSource.class);
        when(dataSource.getConnection()).thenThrow(new CannotCreateTransactionException("connection unavailable"));
        var jdbcTemplate = new JdbcTemplate(dataSource);
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        var repository = new JdbcMigrationRunEvidenceRepository(
                jdbcTemplate,
                new ObjectMapper().findAndRegisterModules());

        assertThatThrownBy(() -> repository.save(evidence("migration-chat-transaction-failure", "chat", now)))
                .isInstanceOf(MigrationRunEvidenceStoreException.class)
                .hasMessage("Failed to persist durable migration run evidence.")
                .hasMessageNotContaining("connection unavailable");
    }

    private MigrationRunEvidence evidence(String runId, String domainKey, Instant now) {
        return new MigrationRunEvidence(
                runId,
                domainKey,
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
                now.plusSeconds(3600));
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private void migrate(DriverManagerDataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
