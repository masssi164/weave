package com.massimotter.weave.backend.meetings.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({MeetingsWorkspaceCapabilityProperties.class})
public class MeetingsWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution meetingsWorkspaceCapabilityContribution(
            MeetingsWorkspaceCapabilityProperties properties) {
        return MeetingsWorkspaceCapabilities.contribution(properties);
    }
}
