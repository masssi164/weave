package com.massimotter.weave.backend.meetings.autoconfigure;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("context")
class MeetingsRuntimeDiscoveryTest {
    @Test
    void discoversActualFutureHandlersWithRequiredChatAdmission() {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.jpa.hibernate.ddl-auto=none", "spring.flyway.enabled=false")
                .run(context -> assertThat(context).hasNotFailed()
                        .hasSingleBean(MeetingsRuntimeAutoConfiguration.class)
                        .hasSingleBean(com.massimotter.weave.backend.meetings.api.MeetingCapsuleController.class)
                        .hasSingleBean(com.massimotter.weave.backend.chat.port.ChatConversationAdmission.class));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
