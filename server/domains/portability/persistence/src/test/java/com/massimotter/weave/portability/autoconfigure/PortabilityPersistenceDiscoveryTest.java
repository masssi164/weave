package com.massimotter.weave.portability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class PortabilityPersistenceDiscoveryTest {
    @Test
    void discoversTheModuleImportsResourceAndCreatesItsRealRepositories() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:portability-discovery;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(PortabilityPersistenceAutoConfiguration.class);
                    assertThat(context).hasSingleBean(EntityManagerFactory.class);
                    assertThat(context).hasBean("transferRunJpaRepository")
                            .hasBean("portabilityPlanJpaRepository").hasBean("portabilityFidelityItemJpaRepository");
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities())
                            .extracting(entity -> entity.getJavaType().getName())
                            .contains("com.massimotter.weave.backend.portability.adapter.PortabilityPlanJpaEntity",
                                    "com.massimotter.weave.backend.portability.adapter.PortabilityFidelityItemJpaEntity");
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
