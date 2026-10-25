package com.massimotter.weave.chat.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ChatPersistenceDiscoveryTest {
    @Test
    void discoversTheModuleImportsResourceAndCreatesItsRealRepositories() {
        new ApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:chat-discovery;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=none",
                        "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ChatPersistenceAutoConfiguration.class);
                    assertThat(context).hasSingleBean(EntityManagerFactory.class);
                    assertThat(context).hasBean("matrixIdentityProjectionJpaRepository");
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities()).isNotEmpty();
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
