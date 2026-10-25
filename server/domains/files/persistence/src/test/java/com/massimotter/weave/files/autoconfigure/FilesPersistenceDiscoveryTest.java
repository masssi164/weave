package com.massimotter.weave.files.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@Tag("context")
class FilesPersistenceDiscoveryTest {
    @Test
    void discoversTheModuleImportsResourceAndCreatesItsRealRepositories() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:files-discovery;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(FilesPersistenceAutoConfiguration.class);
                    assertThat(context).hasSingleBean(EntityManagerFactory.class)
                            .hasSingleBean(com.massimotter.weave.backend.files.port.FilesAuthorityRepository.class)
                            .hasSingleBean(com.massimotter.weave.backend.files.port.FilesUserResourceRepository.class);
                    assertThat(context).hasBean("filesUserResourceJpaRepository");
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).isNotEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
