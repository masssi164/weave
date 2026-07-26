package com.massimotter.weave.backend.service.migration;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.WeavePersistenceConfiguration;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MigrationRunEvidenceRepositoryConfigurationTest {

    @Test
    void jpaModeCreatesTheSingleDurableMigrationEvidenceAuthority() {
        contextRunner().run(context -> {
            assertThat(context).hasSingleBean(MigrationRunEvidenceRepository.class);
            assertThat(context).hasSingleBean(JpaMigrationRunEvidenceRepository.class);
            assertThat(context).doesNotHaveBean(FileMigrationRunEvidenceRepository.class);
        });
    }

    @Test
    void obsoleteFileSelectorCannotAccidentallyCreateASecondAuthority() {
        contextRunner()
                .withPropertyValues("weave.migration.evidence.storage.mode=file")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(MigrationRunEvidenceRepository.class);
                    assertThat(context).doesNotHaveBean(FileMigrationRunEvidenceRepository.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(
                        WeavePersistenceConfiguration.class)
                .withPropertyValues("weave.migration.evidence.storage.mode=jpa")
                .withBean(MigrationRunEvidenceJpaRepository.class,
                        () -> mock(MigrationRunEvidenceJpaRepository.class))
                .withBean(ObjectMapper.class, () -> tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
    }
}
