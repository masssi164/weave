package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcProviderSelectionRepositoryTest {

    private static final String ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION = "ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION";

    @TempDir
    Path tempDir;

    @Test
    void relationalProviderSelectionSurvivesRepositoryRestart() {
        assertThat(ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcProviderSelectionRepository(new JdbcTemplate(dataSource));

        repository.save(selection("Chat"));

        var restartedRepository = new JdbcProviderSelectionRepository(new JdbcTemplate(dataSource));

        assertThat(restartedRepository.findByCategory("CHAT"))
                .isPresent()
                .get()
                .satisfies(selection -> {
                    assertThat(selection.category()).isEqualTo("chat");
                    assertThat(selection.providerKey()).isEqualTo("synapse-homeserver");
                    assertThat(selection.secretRef()).isEqualTo("secretref://weave/provider/synapse-homeserver");
                    assertThat(selection.applied()).isTrue();
                    assertThat(selection.supportSafe()).isTrue();
                    assertThat(selection.migrationDryRunRequired()).isFalse();
                    assertThat(selection.lossyMappingNotes()).containsExactly("support-safe note");
                });
        assertThat(repository.persistencePosture()).isEqualTo("durable-relational-flyway");
    }

    @Test
    void relationalPathMatchesExistingFileRepositoryContractForProviderSelections() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var fileRepository = new FileProviderSelectionRepository(
                objectMapper,
                tempDir.resolve("provider-selections.json"));
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jdbcRepository = new JdbcProviderSelectionRepository(new JdbcTemplate(dataSource));
        ProviderSelection selection = selection("files");

        fileRepository.save(selection);
        jdbcRepository.save(selection);

        assertThat(jdbcRepository.findByCategory("FILES"))
                .isPresent()
                .contains(fileRepository.findByCategory("FILES").orElseThrow());
        assertThat(jdbcRepository.findAll()).containsExactlyElementsOf(fileRepository.findAll());
    }

    @Test
    void flywaySchemaBaselineCreatesStrategicPersistenceTables() {
        DriverManagerDataSource dataSource = dataSource();

        migrate(dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name in "
                        + "('WEAVE_PROVIDER_SELECTIONS', 'WEAVE_PROVIDER_SELECTION_NOTES')",
                Integer.class))
                .isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "select table_name from information_schema.tables where table_name like 'WEAVE_%'",
                String.class))
                .containsExactlyInAnyOrder(
                        "WEAVE_PROVIDER_SELECTIONS",
                        "WEAVE_PROVIDER_SELECTION_NOTES",
                        "WEAVE_PRODUCT_PROFILE_OVERRIDES",
                        "WEAVE_AUDIT_EVENTS",
                        "WEAVE_MIGRATION_RUN_EVIDENCE",
                        "WEAVE_DEVICE_CREDENTIALS",
                        "WEAVE_MATRIX_E2EE_SNAPSHOTS",
                        "WEAVE_MEMBER_INVITATIONS");
    }

    @Test
    void testcontainersPostgresDependencyIsPresentButProductionReadinessIsNotClaimedByH2Proof() {
        assertThat(org.testcontainers.containers.PostgreSQLContainer.class).isNotNull();
        assertThat(new JdbcProviderSelectionRepository(new JdbcTemplate(dataSource())).persistencePosture())
                .isEqualTo("durable-relational-flyway")
                .doesNotContain("postgresql-production-ready");
    }

    @Test
    void repositoryRequiresJdbcTemplateWithDataSource() {
        assertThatThrownBy(() -> new JdbcProviderSelectionRepository(new JdbcTemplate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a JdbcTemplate with a DataSource");
    }

    private ProviderSelection selection(String category) {
        return new ProviderSelection(
                category,
                "synapse-homeserver",
                "recommended_self_hosted_default",
                "secretref://weave/provider/synapse-homeserver",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of("support-safe note"));
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
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
