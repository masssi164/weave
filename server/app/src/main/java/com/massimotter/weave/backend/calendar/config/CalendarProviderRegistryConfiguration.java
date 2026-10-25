package com.massimotter.weave.backend.calendar.config;

import com.massimotter.weave.backend.calendar.domain.CalendarProviderBindingPolicy;
import org.springframework.context.annotation.Import;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import com.massimotter.weave.backend.discovery.provider.RuntimeProviderStatus;
import com.massimotter.weave.backend.discovery.provider.StaticProviderPort;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@Import(CalendarProviderBindingPolicy.class)
public class CalendarProviderRegistryConfiguration {

    @Bean
    ProviderPort calendarProviderRegistrySeam(ObjectProvider<CalendarProviderPort> calendarProviderPort) {
        CalendarProviderPort runtime = calendarProviderPort.getIfAvailable();
        if (runtime == null) {
            return StaticProviderPort.pending(
                    ProviderModule.CALENDAR,
                    "weave-native",
                    "No Calendar runtime adapter is bound; the canonical Calendar facade remains fail-closed.",
                    Set.of("query", "read", "create", "update", "delete", "free-busy"),
                    Set.of("direct-member-provider-api", "credential-exposure", "raw-provider-errors"),
                    List.of("weave-native", "nextcloud-caldav", "microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav"),
                    Map.of("runtimeBindingObserved", false, "facade", "/api/calendar/calendars"));
        }
        return RuntimeProviderStatus.fromConformancePort(
                ProviderModule.CALENDAR,
                runtime.conformanceProfile().adapterKey(),
                runtime.configured(),
                runtime.conformanceProfile(),
                "The selected Calendar adapter is bound behind the canonical Calendar port and the generated User Calendar API.",
                List.of("weave-native", "nextcloud-caldav", "microsoft-graph-calendar", "google-workspace-calendar", "generic-caldav"));
    }
}
