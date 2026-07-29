package com.massimotter.weave.backend.service.migration;

import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class JpaMigrationRunEvidenceRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void postgresCompatibleMigrationPersistsSupportSafeMigrationEvidenceWhenAvailable() {
        DriverManagerDataSource dataSource = postgresDataSource();
        migrate(dataSource);
        Instant now = Instant.parse("2026-05-31T08:00:00Z");
        com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource,
                        com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository.class);
        var repository = new JpaMigrationRunEvidenceRepository(
                springData,
                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());

        repository.save(new MigrationRunEvidence(
                "migration-chat-postgres-001",
                "chat",
                "approved",
                Map.of("Conversation", 2),
                List.of("sha256:3333333333333333333333333333333333333333333333333333333333333333"),
                List.of("audit:migration.dry_run:postgres:001"),
                Map.of("dryRunReportRef", "dry-run:chat:postgres:001"),
                List.of("support-safe migration evidence"),
                true,
                true,
                true,
                now,
                now.plusSeconds(3600)));

        assertThat(repository.findCurrent("migration-chat-postgres-001", "chat", now.plusSeconds(30)))
                .isPresent()
                .get()
                .satisfies(evidence -> {
                    assertThat(evidence.lifecycle()).isEqualTo("approved");
                    assertThat(evidence.providerDiagnostics()).contains("support-safe migration evidence");
                });
    }

    private DriverManagerDataSource postgresDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void migrate(DriverManagerDataSource dataSource) {
        JpaTestDatabase.initializeSchema(dataSource);
    }
}
