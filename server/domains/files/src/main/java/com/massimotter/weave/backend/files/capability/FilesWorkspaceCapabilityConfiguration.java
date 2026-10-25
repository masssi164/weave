package com.massimotter.weave.backend.files.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({FilesWorkspaceCapabilityProperties.class})
public class FilesWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution filesWorkspaceCapabilityContribution(
            FilesWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        return FilesWorkspaceCapabilities.contribution(properties, health);
    }
}
