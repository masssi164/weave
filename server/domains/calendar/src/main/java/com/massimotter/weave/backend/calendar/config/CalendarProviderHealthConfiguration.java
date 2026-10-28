package com.massimotter.weave.backend.calendar.config;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.health.ProviderHealthContribution;
import com.massimotter.weave.backend.health.ProviderCapabilityProbeResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CalendarProviderHealthConfiguration {
    @Bean
    ProviderHealthContribution calendarProviderHealth(ObjectProvider<CalendarProviderPort> providers) {
        CalendarProviderPort provider = providers.getIfUnique();
        return new ProviderHealthContribution("calendar", () -> provider != null && provider.configured(),
                () -> provider == null ? ProviderCapabilityProbeResult.unavailable("calendar-adapter-unavailable") : provider.healthProbe());
    }
}
