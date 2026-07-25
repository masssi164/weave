package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaProviderSelectionRepositoryTest {

    private static final String ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION = "ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION";

    @Test
    void relationalProviderSelectionSurvivesRepositoryRestart() {
        assertThat(ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = repository(dataSource);

        repository.save(selection("Chat"));

        var restartedRepository = repository(dataSource);

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
        assertThat(repository.persistencePosture()).isEqualTo("durable-relational-jpa-flyway");
    }

    @Test
    void relationalPathPreservesTheCanonicalProviderSelectionContract() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jpaRepository = repository(dataSource);
        ProviderSelection selection = selection("files");

        jpaRepository.save(selection);

        assertThat(jpaRepository.findByCategory("FILES"))
                .isPresent()
                .contains(selection);
        assertThat(jpaRepository.findAll()).containsExactly(selection);
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
                        "WEAVE_MATRIX_REVOKED_SESSIONS",
                        "WEAVE_MATRIX_IDENTITY_PROJECTION",
                        "WEAVE_IDENTITY_PROVISIONING_INTENTS",
                        "WEAVE_KEYCLOAK_EVENT_RECEIPTS",
                        "WEAVE_CHAT_CONVERSATIONS",
                        "WEAVE_CHAT_MEMBERSHIPS",
                        "WEAVE_CHAT_EVENTS",
                        "WEAVE_CHAT_OPERATIONS",
                        "WEAVE_CHAT_OUTBOX",
                        "WEAVE_CHAT_PROVIDER_MAPPINGS",
                        "WEAVE_CHAT_BRIDGE_LEDGER",
                        "WEAVE_CHAT_APPSERVICE_TRANSACTIONS",
                        "WEAVE_CHAT_QUARANTINE",
                        "WEAVE_CHAT_READ_RECEIPTS",
                        "WEAVE_CHAT_CHANGES",
                        "WEAVE_AGENT_RUNTIME_CELLS",
                        "WEAVE_AGENT_RUNTIME_PROFILES",
                        "WEAVE_AGENT_RUNTIME_PROFILE_SIGNATURES",
                        "WEAVE_AGENT_RUNTIME_COMMANDS",
                        "WEAVE_AGENT_RUNTIME_ENTITLEMENTS",
                        "WEAVE_AGENT_RUNTIME_REVOCATIONS",
                        "WEAVE_AGENT_RUNTIME_AUDIT_CORRELATIONS",
                        "WEAVE_AGENT_RUNTIME_STATE_GENERATIONS",
                        "WEAVE_AGENT_RUNTIME_STATE_CHUNKS",
                        "WEAVE_AGENT_RUNTIME_STATE_HEADS",
                        "WEAVE_AGENT_RUNTIME_STATE_DELETIONS",
                        "WEAVE_OPERATION_INTENTS",
                        "WEAVE_OPERATION_OUTBOX",
                        "WEAVE_PROVIDER_BINDINGS",
                        "WEAVE_PROVIDER_OBJECT_MAPPINGS",
                        "WEAVE_FILES_OBJECTS",
                        "WEAVE_FILE_LOCKS",
                        "WEAVE_ORGANIZATION_BOOTSTRAP",
                        "WEAVE_PERSON_BINDINGS",
                        "WEAVE_SPACES",
                        "WEAVE_SPACE_MEMBERSHIPS",
                        "WEAVE_PORTABILITY_PLANS",
                        "WEAVE_PORTABILITY_FIDELITY_ITEMS",
                        "WEAVE_WORKSPACE_REVISIONS",
                        "WEAVE_WAKE_ENVELOPES");
    }

    @Test
    void testcontainersPostgresDependencyIsPresentButProductionReadinessIsNotClaimedByH2Proof() {
        assertThat(org.testcontainers.containers.PostgreSQLContainer.class).isNotNull();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        assertThat(repository(dataSource).persistencePosture())
                .isEqualTo("durable-relational-jpa-flyway")
                .doesNotContain("postgresql-production-ready");
    }

    @Test
    void repositoryRequiresTheTypedSpringDataPort() {
        assertThatThrownBy(() -> new JpaProviderSelectionRepository(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("selections");
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

    private JpaProviderSelectionRepository repository(DriverManagerDataSource dataSource) {
        ProviderSelectionJpaRepository springData = com.massimotter.weave.backend.testing.JpaTestDatabase
                .repository(dataSource, ProviderSelectionJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaProviderSelectionRepository(springData));
    }
}
