package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.persistence.jpa.profile.ProductProfileOverrideJpaRepository;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JpaProductProfileOverrideRepositoryTest {

    @Test
    void profileSurvivesAdapterRestartWithStructuredAccessibilityPreferences() {
        DriverManagerDataSource dataSource = migratedDataSource();
        JpaProductProfileOverrideRepository repository = repository(dataSource);
        ProductProfileOverride profile = new ProductProfileOverride(
                "Massimo",
                "weave://avatars/canonical",
                "de-DE",
                "Europe/Berlin",
                Map.of("reducedMotion", "true", "contrast", "high"),
                "organization");

        repository.saveForPrimaryIdentityKey("issuer#subject", profile);
        ProductProfileOverride updated = new ProductProfileOverride(
                "Massimo Updated",
                "weave://avatars/updated",
                "en-GB",
                "Europe/London",
                Map.of("reducedMotion", "false"),
                "private");
        repository.saveForPrimaryIdentityKey("issuer#subject", updated);

        assertThat(repository(dataSource).findByPrimaryIdentityKey("issuer#subject")).isEqualTo(updated);
        assertThat(repository.findByPrimaryIdentityKey(" ")).isNull();
        assertThat(repository.persistencePosture()).isEqualTo("portable-jpa-hibernate-validated");
    }

    private JpaProductProfileOverrideRepository repository(DriverManagerDataSource dataSource) {
        ProductProfileOverrideJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, ProductProfileOverrideJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource,
                new JpaProductProfileOverrideRepository(
                        springData, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .entityFirstDataSource("product-profile");
    }
}
