package com.massimotter.weave.backend.portability.application;

import com.massimotter.weave.backend.portability.persistence.JpaMigrationRunEvidenceRepository;

import com.massimotter.weave.backend.portability.port.MigrationRunEvidenceRepository;

import com.massimotter.weave.backend.portability.persistence.MigrationRunEvidenceJpaRepository;
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
    void obsoleteFileSelectorCannotDisableOrReplaceTheJpaAuthority() {
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
                .withBean(JpaMigrationRunEvidenceRepository.class,
                        () -> new JpaMigrationRunEvidenceRepository(
                                mock(MigrationRunEvidenceJpaRepository.class),
                                tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build()));
    }
}
