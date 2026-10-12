package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarBindingBootstrapConfigurationTest {
    private final ProviderBindingRepository repository = mock(ProviderBindingRepository.class);
    private final CalendarBindingBootstrapConfiguration config = new CalendarBindingBootstrapConfiguration();
    private final ContextAuthorizationProperties context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
    @Test
    void createsOnlyAnExplicitMatchingInitialBinding() throws Exception {
        when(repository.current("tenant-default", "calendar")).thenReturn(Optional.empty());
        config.calendarBindingBootstrap(repository, properties("tenant-default"), context).run(null);
        verify(repository).activate(eq("tenant-default"), eq("calendar"), eq(0L), eq("weave-native"), eq(CalendarUserApiService.CONFIGURATION_REF), any());
    }
    @Test
    void foreignOrganizationOrConflictingAuthorityCannotBeReplaced() {
        assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("foreign"), context).run(null)).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(repository);
        when(repository.current("tenant-default", "calendar")).thenReturn(Optional.of(new ProviderBinding("tenant-default", "calendar", 2,
                "another-provider", CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.EPOCH)));
        assertThatThrownBy(() -> config.calendarBindingBootstrap(repository, properties("tenant-default"), context).run(null)).isInstanceOf(IllegalStateException.class);
        verify(repository, never()).activate(anyString(), anyString(), anyLong(), anyString(), anyString(), any());
    }
    private CalendarBindingBootstrapConfiguration.Properties properties(String organization) {
        return new CalendarBindingBootstrapConfiguration.Properties(true, organization, "weave-native", CalendarUserApiService.CONFIGURATION_REF);
    }
}
