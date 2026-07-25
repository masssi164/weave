package com.massimotter.weave.backend.service;

import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaProductProfileOverrideRepositoryTest {

    private static final String ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION =
            "ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION";

    @Test
    void relationalProductProfileOverrideSurvivesRepositoryRestart() {
        assertThat(ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = repository(dataSource);

        repository.saveForPrimaryIdentityKey("issuer+subject:https://auth.weave.test/realms/weave#user-123", profile());

        var restartedRepository = repository(dataSource);

        assertThat(restartedRepository.findByPrimaryIdentityKey(
                        "issuer+subject:https://auth.weave.test/realms/weave#user-123"))
                .satisfies(profile -> {
                    assertThat(profile.displayName()).isEqualTo("Alice Durable");
                    assertThat(profile.avatar()).isEqualTo("weave-avatar://user/alice");
                    assertThat(profile.locale()).isEqualTo("de-DE");
                    assertThat(profile.timezone()).isEqualTo("Europe/Berlin");
                    assertThat(profile.accessibilityPreferences()).containsEntry("reducedMotion", "true");
                    assertThat(profile.profileVisibility()).isEqualTo("private");
                });
        assertThat(repository.persistencePosture()).isEqualTo("durable-relational-jpa-flyway");
    }

    @Test
    void relationalPathPreservesTheCanonicalProfileOverrideContract() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jpaRepository = repository(dataSource);
        String primaryIdentityKey = "issuer+subject:https://auth.weave.test/realms/weave#user-123";

        jpaRepository.saveForPrimaryIdentityKey(primaryIdentityKey, profile());

        assertThat(jpaRepository.findByPrimaryIdentityKey(primaryIdentityKey))
                .isEqualTo(profile());
    }

    @Test
    void repositoriesRejectNullProfileOverridesConsistently() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jpaRepository = repository(dataSource);

        assertThatThrownBy(() -> jpaRepository.saveForPrimaryIdentityKey(
                "issuer+subject:https://auth.weave.test/realms/weave#user-123",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
    }

    @Test
    void flywaySchemaBaselineAddsProductProfileTableWithoutProductionReadinessClaim() {
        DriverManagerDataSource dataSource = dataSource();

        migrate(dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'WEAVE_PRODUCT_PROFILE_OVERRIDES'",
                Integer.class))
                .isEqualTo(1);
        assertThat(repository(dataSource).persistencePosture())
                .isEqualTo("durable-relational-jpa-flyway")
                .doesNotContain("postgresql-production-ready");
    }

    @Test
    void repositoryRequiresTypedSpringDataDependencies() {
        assertThatThrownBy(() -> new JpaProductProfileOverrideRepository(
                null,
                new ObjectMapper()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("profiles");
    }

    private ProductProfileOverride profile() {
        return new ProductProfileOverride(
                "Alice Durable",
                "weave-avatar://user/alice",
                "de-DE",
                "Europe/Berlin",
                Map.of("reducedMotion", "true"),
                "private");
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

    private JpaProductProfileOverrideRepository repository(DriverManagerDataSource dataSource) {
        ProductProfileOverrideJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource,
                        ProductProfileOverrideJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaProductProfileOverrideRepository(
                        springData,
                        tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }
}
