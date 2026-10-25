package com.massimotter.weave.backend.devops.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({DevopsWorkspaceCapabilityProperties.class})
public class DevopsWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution devopsWorkspaceCapabilityContribution(
            DevopsWorkspaceCapabilityProperties properties) {
        return DevopsWorkspaceCapabilities.contribution(properties);
    }
}
