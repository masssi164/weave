package com.massimotter.weave.chat.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import com.massimotter.weave.backend.chat.persistence.MatrixE2eeRelationalStore;
import com.massimotter.weave.backend.chat.port.MatrixE2eePersistence;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

@Tag("context")
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
                    assertThat(context).doesNotHaveBean(MatrixE2eeRelationalStore.class);
                    assertThat(context.getBean(EntityManagerFactory.class).getMetamodel().getEntities())
                            .extracting(jakarta.persistence.metamodel.EntityType::getName)
                            .contains("MatrixSyncHead", "MatrixDevice", "MatrixOneTimeKey", "MatrixFallbackKey",
                                    "MatrixCrossSigningKey", "MatrixToDeviceMessage", "MatrixSyncProgress",
                                    "MatrixAccountData", "MatrixBackupVersion", "MatrixBackupSession",
                                    "MatrixSharedUser", "MatrixSharedUserChange", "MatrixOidcBinding",
                                    "MatrixDeviceProof", "MatrixRecoveryChallenge");
                });
    }

    @Test
    void servingStoreIsDiscoveredExactlyOnceThroughModuleImports() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues(
                        "spring.datasource.url=jdbc:h2:mem:chat-serving-discovery;DB_CLOSE_DELAY=-1",
                        "spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed()
                            .hasSingleBean(MatrixE2eeStoreAutoConfiguration.class)
                            .hasSingleBean(MatrixE2eeRelationalStore.class)
                            .hasSingleBean(MatrixE2eePersistence.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
