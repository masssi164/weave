package com.massimotter.weave.backend.service.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MigrationRunEvidenceRepositoryConfigurationTest {

    @Test
    void migrationEvidenceHasOneJpaAuthority() {
        contextRunner().run(context -> {
            assertThat(context).hasSingleBean(MigrationRunEvidenceRepository.class);
            assertThat(context).hasSingleBean(JpaMigrationRunEvidenceRepository.class);
            assertThat(context).doesNotHaveBean(FileMigrationRunEvidenceRepository.class);
        });
    }

    @Test
    void obsoleteStorageSelectorsCannotRestoreTheFileFallback() {
        contextRunner()
                .withPropertyValues("weave.migration.evidence.storage.mode=file")
                .run(context -> {
                    assertThat(context).hasSingleBean(MigrationRunEvidenceRepository.class);
                    assertThat(context).hasSingleBean(JpaMigrationRunEvidenceRepository.class);
                    assertThat(context).doesNotHaveBean(FileMigrationRunEvidenceRepository.class);
                });
    }

    private ApplicationContextRunner contextRunner() {
        return new ApplicationContextRunner()
                .withUserConfiguration(JpaMigrationRunEvidenceRepository.class)
                .withBean(ObjectMapper.class, () -> tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build())
                .withBean(
                        MigrationRunEvidenceJpaRepository.class,
                        () -> mock(MigrationRunEvidenceJpaRepository.class));
    }
}
