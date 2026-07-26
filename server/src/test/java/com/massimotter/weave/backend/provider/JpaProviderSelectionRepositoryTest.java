package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.persistence.jpa.provider.ProviderSelectionJpaRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

// ENTERPRISE_TARGET_PERSISTENCE_FOUNDATION
class JpaProviderSelectionRepositoryTest {

    @Test
    void selectionSurvivesAdapterRestartAndRetainsCanonicalOrdering() {
        DriverManagerDataSource dataSource = migratedDataSource();
        JpaProviderSelectionRepository repository = repository(dataSource);
        ProviderSelection selection = new ProviderSelection(
                "Files",
                "nextcloud",
                "recommended_self_hosted_default",
                "secretref://weave/provider/nextcloud",
                "actor:admin-123",
                Instant.parse("2026-05-31T08:00:00Z"),
                true,
                true,
                false,
                List.of("support-safe note"));

        repository.save(selection);

        JpaProviderSelectionRepository restarted = repository(dataSource);
        assertThat(restarted.findByCategory("FILES")).get().satisfies(restored -> {
            assertThat(restored.category()).isEqualTo("files");
            assertThat(restored.providerKey()).isEqualTo("nextcloud");
            assertThat(restored.secretRef()).isEqualTo("secretref://weave/provider/nextcloud");
            assertThat(restored.lossyMappingNotes()).containsExactly("support-safe note");
        });
        assertThat(restarted.findAll()).hasSize(1);
        assertThat(restarted.persistencePosture()).isEqualTo("portable-jpa-hibernate-validated");
    }

    private JpaProviderSelectionRepository repository(DriverManagerDataSource dataSource) {
        ProviderSelectionJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, ProviderSelectionJpaRepository.class);
        return com.massimotter.weave.backend.testing.JpaTestDatabase.transactional(
                dataSource, new JpaProviderSelectionRepository(springData));
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .migratedDataSource("provider-selection");
    }
}
