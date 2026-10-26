package com.massimotter.weave.backend.calendar.capability;

import com.massimotter.weave.backend.capability.port.WorkspaceCapabilityContribution;
import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wires the owning domain's capability declaration into shared workspace policy. */
@org.springframework.context.annotation.PropertySource("classpath:weave/domain/calendar-defaults.properties")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({CalendarWorkspaceCapabilityProperties.class})
public class CalendarWorkspaceCapabilityConfiguration {

    @Bean
    public WorkspaceCapabilityContribution calendarWorkspaceCapabilityContribution(
            CalendarWorkspaceCapabilityProperties properties,
            ProviderCapabilityHealthService health) {
        return CalendarWorkspaceCapabilities.contribution(properties, health);
    }
}
