package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.service.calendar.CalendarProviderBindingReadiness;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarBindingBootstrapConfigurationTest {
    private final ProviderBindingRepository repository = mock(ProviderBindingRepository.class);
    private final CalendarProviderPort provider = mock(CalendarProviderPort.class);
    private CalendarProviderBindingReadiness readiness;
    @BeforeEach void setup() {
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "weave-native", Set.of(), Map.of(), true, true, true));
        when(provider.configured()).thenReturn(true);
        readiness = new CalendarProviderBindingReadiness(repository, List.of(provider));
    }
    private final CalendarBindingBootstrapConfiguration config = new CalendarBindingBootstrapConfiguration();
    private final ContextAuthorizationProperties context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    @Test
    void createsOnlyAnExplicitMatchingInitialBinding() throws Exception {
        when(repository.current("tenant-default", "calendar")).thenReturn(Optional.empty());
        config.calendarBindingBootstrap(repository, properties("tenant-default"), context, readiness).run(null);
        verify(repository).activate(eq("tenant-default"), eq("calendar"), eq(0L), eq("weave-native"), eq(CalendarUserApiService.CONFIGURATION_REF), any());
    }
    @Test
    void foreignOrganizationOrConflictingAuthorityCannotBeReplaced() {
        assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("foreign"), context, readiness).run(null)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
        when(repository.current("tenant-default", "calendar")).thenReturn(Optional.of(new ProviderBinding("tenant-default", "calendar", 2,
                "another-provider", CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH)));
        assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("tenant-default"), context, readiness).run(null)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).activate(anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }
    @Test
    void matchingCurrentAuthorityIsPreservedAcrossRepeatedStartup() throws Exception {
        var current = new ProviderBinding("tenant-default", "calendar", 3, "weave-native",
                CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH);
        when(repository.current("tenant-default", "calendar")).thenReturn(Optional.of(current));
        var startup = config.calendarBindingBootstrap(repository, properties("tenant-default"), context, readiness);
        startup.run(null);
        startup.run(null);
        verify(repository, never()).activate(anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    @Test
    void inactiveForeignDomainAndUnconfiguredAdapterFailWithoutWriting() {
        for (var binding : List.of(
                new ProviderBinding("tenant-default", "calendar", 1, "weave-native", CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.RETIRED, Instant.EPOCH),
                new ProviderBinding("foreign", "calendar", 1, "weave-native", CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH),
                new ProviderBinding("tenant-default", "files", 1, "weave-native", CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH))) {
            when(repository.current("tenant-default", "calendar")).thenReturn(Optional.of(binding));
            assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("tenant-default"), context, readiness).run(null))
                    .isInstanceOf(IllegalStateException.class);
        }
        when(provider.configured()).thenReturn(false);
        assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("tenant-default"), context, readiness).run(null))
                .isInstanceOf(com.massimotter.weave.backend.api.error.ApiErrorException.class);
        verify(repository, never()).activate(anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }

    private CalendarBindingBootstrapConfiguration.Properties properties(String organization) {
        return new CalendarBindingBootstrapConfiguration.Properties(true, organization, "weave-native", CalendarUserApiService.CONFIGURATION_REF);
    }
}
