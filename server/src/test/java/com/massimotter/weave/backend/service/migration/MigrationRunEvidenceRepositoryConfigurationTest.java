package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationRunEvidenceRepositoryConfigurationTest {

    @Test
    void migrationEvidenceDefaultsToFileBackedRepository() {
        contextRunner().run(context -> {
            assertThat(context).hasSingleBean(MigrationRunEvidenceRepository.class);
            assertThat(context).hasSingleBean(FileMigrationRunEvidenceRepository.class);
            assertThat(context).doesNotHaveBean(JdbcMigrationRunEvidenceRepository.class);
        });
    }

    @Test
    void jdbcMigrationEvidenceStorageModeDisablesFileRepositoryAndEnablesJdbcRepository() {
        contextRunner()
                .withPropertyValues(
                        "weave.migration.evidence.storage.mode=jdbc",
                        "weave.persistence.jdbc.url=jdbc:h2:mem:" + UUID.randomUUID()
                                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1",
                        "weave.persistence.jdbc.username=sa",
                        "weave.persistence.jdbc.password=",
                        "weave.persistence.jdbc.driver-class-name=org.h2.Driver")
                .run(context -> {
                    assertThat(context).hasSingleBean(MigrationRunEvidenceRepository.class);
                    assertThat(context).hasSingleBean(JdbcMigrationRunEvidenceRepository.class);
                    assertThat(context).doesNotHaveBean(FileMigrationRunEvidenceRepository.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        WeavePersistenceConfiguration.class,
                        FileMigrationRunEvidenceRepository.class,
                        JdbcMigrationRunEvidenceRepository.class)
                .withBean(ObjectMapper.class, () -> new ObjectMapper().findAndRegisterModules());
    }
}
