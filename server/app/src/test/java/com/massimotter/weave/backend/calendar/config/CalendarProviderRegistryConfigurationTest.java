package com.massimotter.weave.backend.calendar.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class CalendarProviderRegistryConfigurationTest {
    private final CalendarProviderRegistryConfiguration configuration = new CalendarProviderRegistryConfiguration();

    @Test
    void providerStatusNamesActiveUserApiRatherThanDisabledPublicDav() {
        @SuppressWarnings("unchecked")
        ObjectProvider<CalendarProviderPort> noCalendar = mock(ObjectProvider.class);
        ProviderStatusResponse calendar = configuration.calendarProviderRegistrySeam(noCalendar).status();
        assertThat(calendar.diagnostics()).containsEntry("facade", "/api/calendar/calendars");
        assertThat(calendar.configured()).isFalse();
        assertThat(calendar.failClosed()).isTrue();
        CalendarProviderPort runtime = mock(CalendarProviderPort.class);
        when(runtime.configured()).thenReturn(true);
        when(runtime.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "weave-native", Set.of("query", "read"), Map.of(), true, true, true));
        when(noCalendar.getIfAvailable()).thenReturn(runtime);
        ProviderStatusResponse boundCalendar = configuration.calendarProviderRegistrySeam(noCalendar).status();
        assertThat(boundCalendar.summary()).contains("generated User Calendar API").doesNotContain("/caldav");
    }
}
