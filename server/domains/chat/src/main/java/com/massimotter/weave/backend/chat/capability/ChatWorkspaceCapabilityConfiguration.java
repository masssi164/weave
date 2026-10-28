package com.massimotter.weave.backend.chat.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@org.springframework.context.annotation.PropertySource("classpath:weave/domain/chat-defaults.properties")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ChatWorkspaceCapabilityProperties.class})
public class ChatWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution chatWorkspaceCapabilityContribution(
            ChatWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        return ChatWorkspaceCapabilities.contribution(properties, health);
    }
}
