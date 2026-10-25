package com.massimotter.weave.backend.decisions.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DecisionsWorkspaceCapabilityProperties.class})
public class DecisionsWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution decisionsWorkspaceCapabilityContribution(
            DecisionsWorkspaceCapabilityProperties properties) {
        return DecisionsWorkspaceCapabilities.contribution(properties);
    }
}
