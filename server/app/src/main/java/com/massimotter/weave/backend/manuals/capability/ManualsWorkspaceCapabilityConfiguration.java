package com.massimotter.weave.backend.manuals.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({ManualsWorkspaceCapabilityProperties.class})
public class ManualsWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution manualsWorkspaceCapabilityContribution(
            ManualsWorkspaceCapabilityProperties properties) {
        return ManualsWorkspaceCapabilities.contribution(properties);
    }
}
