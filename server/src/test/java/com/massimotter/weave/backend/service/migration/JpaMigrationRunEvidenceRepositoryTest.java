package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

// ENTERPRISE_TARGET_MIGRATION_EVIDENCE_PERSISTENCE_FOUNDATION
class JpaMigrationRunEvidenceRepositoryTest {

    @Test
    void evidenceSurvivesAdapterRestartAndExpiresAtTheContractBoundary() {
        DriverManagerDataSource dataSource = migratedDataSource();
        JpaMigrationRunEvidenceRepository repository = repository(dataSource);
        MigrationRunEvidence evidence = new MigrationRunEvidence(
                "run-1",
                "files",
                "verified",
                Map.of("files", 12),
                List.of("sha256:content"),
                List.of("audit:1"),
                Map.of("manifest", "artifact:manifest"),
                List.of("support-safe"),
                true,
                true,
                false,
                Instant.parse("2026-07-09T10:00:00Z"),
                Instant.parse("2026-07-09T12:00:00Z"));

        repository.save(evidence);

        assertThat(repository(dataSource).findCurrent(
                        "run-1", "files", Instant.parse("2026-07-09T11:59:59Z")))
                .contains(evidence);
        assertThat(repository(dataSource).findCurrent(
                        "run-1", "files", Instant.parse("2026-07-09T12:00:00Z")))
                .isEmpty();
        assertThat(repository.persistencePosture()).isEqualTo("portable-jpa-hibernate-validated");
    }

    private JpaMigrationRunEvidenceRepository repository(DriverManagerDataSource dataSource) {
        MigrationRunEvidenceJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, MigrationRunEvidenceJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaMigrationRunEvidenceRepository(
                        springData, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .entityFirstDataSource("migration-evidence");
    }
}
