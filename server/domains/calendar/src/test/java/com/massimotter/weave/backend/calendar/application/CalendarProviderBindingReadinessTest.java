package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.providerbinding.contract.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class CalendarProviderBindingReadinessTest {
    private final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    private final CalendarProviderPort provider = mock(CalendarProviderPort.class);
    private CalendarProviderBindingReadiness readiness;

    @BeforeEach void setup() {
        when(provider.conformanceProfile()).thenReturn(profile("calendar", "weave-native"));
        when(provider.configured()).thenReturn(true);
        readiness = new CalendarProviderBindingReadiness(bindings, List.of(provider));
        when(bindings.current("org:current", "calendar")).thenReturn(Optional.of(binding(
                "org:current", "calendar", "weave-native", CalendarProviderBindingReadiness.CONFIGURATION_REF,
                ProviderBinding.State.ACTIVE)));
    }

    @Test void exactCurrentAuthorityIsReadyWithoutClaimingOrExecutingCrud() {
        var bound = readiness.require("org:current");
        assertThat(bound.provider()).isSameAs(provider);
        assertThat(bound.binding().adapterKey()).isEqualTo("weave-native");
        verify(provider).conformanceProfile();
        verify(provider).configured();
        verifyNoMoreInteractions(provider);
    }

    @Test void missingInactiveForeignWrongDomainConfigurationAndAdapterFailClosed() {
        for (var current : List.of(Optional.<ProviderBinding>empty(),
                Optional.of(binding("org:current", "calendar", "weave-native", CalendarProviderBindingReadiness.CONFIGURATION_REF, ProviderBinding.State.RETIRED)),
                Optional.of(binding("org:other", "calendar", "weave-native", CalendarProviderBindingReadiness.CONFIGURATION_REF, ProviderBinding.State.ACTIVE)),
                Optional.of(binding("org:current", "files", "weave-native", CalendarProviderBindingReadiness.CONFIGURATION_REF, ProviderBinding.State.ACTIVE)),
                Optional.of(binding("org:current", "calendar", "weave-native", "configuration:other", ProviderBinding.State.ACTIVE)),
                Optional.of(binding("org:current", "calendar", "unknown-adapter", CalendarProviderBindingReadiness.CONFIGURATION_REF, ProviderBinding.State.ACTIVE)))) {
            when(bindings.current("org:current", "calendar")).thenReturn(current);
            assertUnavailable(() -> readiness.require("org:current"));
        }
        assertUnavailable(() -> readiness.require(""));
        assertUnavailable(() -> readiness.require(null));
    }

    @Test void unconfiguredAdapterAndStaleAuthorityNeverPassPreparation() {
        var bound = readiness.require("org:current");
        when(provider.configured()).thenReturn(false);
        assertUnavailable(() -> readiness.require("org:current"));
        when(provider.configured()).thenReturn(true);
        when(bindings.current("org:current", "calendar")).thenReturn(Optional.empty());
        assertUnavailable(() -> readiness.requireUnchanged("org:current", bound));
    }

    @Test void ambiguousOrWrongDomainCatalogIsRejected() {
        assertThatIllegalStateException().isThrownBy(() -> new CalendarProviderBindingReadiness(bindings, List.of(provider, provider)));
        when(provider.conformanceProfile()).thenReturn(profile("files", "weave-native"));
        assertThatIllegalStateException().isThrownBy(() -> new CalendarProviderBindingReadiness(bindings, List.of(provider)));
    }

    private static void assertUnavailable(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiErrorException.class,
                error -> assertThat(error.code()).isEqualTo("calendar-provider-unavailable"));
    }
    private static ProviderBinding binding(String organization, String domain, String adapter, String configuration, ProviderBinding.State state) {
        return new ProviderBinding(organization, domain, 1, adapter, configuration, state, Instant.EPOCH);
    }
    private static ProviderConformanceProfile profile(String domain, String adapter) {
        return new ProviderConformanceProfile(domain, adapter, Set.of(), Map.of(), true, true, true);
    }
}
