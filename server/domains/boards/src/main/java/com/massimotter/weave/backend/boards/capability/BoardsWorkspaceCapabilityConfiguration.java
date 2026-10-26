package com.massimotter.weave.backend.boards.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@org.springframework.context.annotation.PropertySource("classpath:weave/domain/boards-defaults.properties")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({BoardsWorkspaceCapabilityProperties.class})
public class BoardsWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution boardsWorkspaceCapabilityContribution(
            BoardsWorkspaceCapabilityProperties properties) {
        return BoardsWorkspaceCapabilities.contribution(properties);
    }
}
