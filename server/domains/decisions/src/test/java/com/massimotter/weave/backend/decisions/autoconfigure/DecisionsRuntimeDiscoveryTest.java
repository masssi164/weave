package com.massimotter.weave.backend.decisions.autoconfigure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("context")
class DecisionsRuntimeDiscoveryTest {
    @Test
    void discoversActualFutureHandlersWithRequiredChatAdmission() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(DecisionsRuntimeAutoConfiguration.class)
                        .hasSingleBean(com.massimotter.weave.backend.decisions.api.DecisionLedgerController.class)
                        .hasSingleBean(com.massimotter.weave.backend.chat.port.ChatConversationAdmission.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
