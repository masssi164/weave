package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.chat.adapter.WeaveCanonicalChatAdapter;
import com.massimotter.weave.backend.chat.port.ChatProviderPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Test-classpath-only chat provider for isolated Spring and acceptance tests. */
@Configuration(proxyBeanMethods = false)
@Profile("test")
public class TestChatRuntimeConfiguration {

    @Bean
    ChatProviderPort testChatProviderPort() {
        return new WeaveCanonicalChatAdapter();
    }
}
