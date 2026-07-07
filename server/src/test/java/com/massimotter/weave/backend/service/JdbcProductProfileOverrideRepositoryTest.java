package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcProductProfileOverrideRepositoryTest {

    private static final String ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION =
            "ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION";

    @TempDir
    Path tempDir;

    @Test
    void relationalProductProfileOverrideSurvivesRepositoryRestart() {
        assertThat(ENTERPRISE_TARGET_PROFILE_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var repository = new JdbcProductProfileOverrideRepository(new JdbcTemplate(dataSource));

        repository.saveForPrimaryIdentityKey("issuer+subject:https://auth.weave.test/realms/weave#user-123", profile());

        var restartedRepository = new JdbcProductProfileOverrideRepository(new JdbcTemplate(dataSource));

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
        assertThat(repository.persistencePosture()).isEqualTo("durable-relational-flyway");
    }

    @Test
    void relationalPathMatchesCurrentPrimaryKeyFileRepositoryContractForProfileOverrides() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var fileRepository = new FileProductProfileOverrideRepository(
                objectMapper,
                tempDir.resolve("profile-overrides.json"));
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jdbcRepository = new JdbcProductProfileOverrideRepository(new JdbcTemplate(dataSource));
        String primaryIdentityKey = "issuer+subject:https://auth.weave.test/realms/weave#user-123";

        fileRepository.saveForPrimaryIdentityKey(primaryIdentityKey, profile());
        jdbcRepository.saveForPrimaryIdentityKey(primaryIdentityKey, profile());

        assertThat(jdbcRepository.findByPrimaryIdentityKey(primaryIdentityKey))
                .isEqualTo(fileRepository.findByPrimaryIdentityKey(primaryIdentityKey));
    }

    @Test
    void repositoriesRejectNullProfileOverridesConsistently() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var fileRepository = new FileProductProfileOverrideRepository(
                objectMapper,
                tempDir.resolve("profile-overrides.json"));
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jdbcRepository = new JdbcProductProfileOverrideRepository(new JdbcTemplate(dataSource));

        assertThatThrownBy(() -> fileRepository.saveForPrimaryIdentityKey(
                "issuer+subject:https://auth.weave.test/realms/weave#user-123",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be null");
        assertThatThrownBy(() -> jdbcRepository.saveForPrimaryIdentityKey(
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
        assertThat(new JdbcProductProfileOverrideRepository(new JdbcTemplate(dataSource)).persistencePosture())
                .isEqualTo("durable-relational-flyway")
                .doesNotContain("postgresql-production-ready");
    }

    @Test
    void repositoryRequiresJdbcTemplateWithDataSource() {
        assertThatThrownBy(() -> new JdbcProductProfileOverrideRepository(new JdbcTemplate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a JdbcTemplate with a DataSource");
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
}
