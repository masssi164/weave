package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Current Calendar routing authority, shared by member operations and preparation; no CRUD proof. */
public class CalendarProviderBindingReadiness {
    public static final String CONFIGURATION_REF = "configuration:calendar:deployment";
    private final ProviderBindingRepository bindings;
    private final Map<String, CalendarProviderPort> providers;

    public CalendarProviderBindingReadiness(ProviderBindingRepository bindings, List<CalendarProviderPort> providers) {
        this.bindings = bindings;
        Map<String, CalendarProviderPort> keyed = new HashMap<>();
        for (CalendarProviderPort provider : providers) {
            var profile = provider.conformanceProfile();
            if (!"calendar".equals(profile.domain())
                    || keyed.putIfAbsent(profile.adapterKey(), provider) != null) {
                throw new IllegalStateException("Calendar adapter catalog is invalid or ambiguous");
            }
        }
        this.providers = Map.copyOf(keyed);
    }

    public Bound require(String organizationRef) {
        if (organizationRef == null || organizationRef.isBlank()) throw unavailable();
        ProviderBinding binding = bindings.current(organizationRef, "calendar")
                .filter(value -> value.state() == ProviderBinding.State.ACTIVE
                        && organizationRef.equals(value.organizationRef())
                        && "calendar".equals(value.domain())
                        && CONFIGURATION_REF.equals(value.configurationRef()))
                .orElseThrow(CalendarProviderBindingReadiness::unavailable);
        return new Bound(binding, requireConfiguredAdapter(binding.adapterKey()));
    }

    public CalendarProviderPort requireConfiguredAdapter(String adapterKey) {
        if (adapterKey == null || adapterKey.isBlank()) throw unavailable();
        CalendarProviderPort provider = providers.get(adapterKey);
        if (provider == null || !provider.configured()) {
            throw unavailable();
        }
        return provider;
    }

    public void requireUnchanged(String organizationRef, Bound expected) {
        if (!require(organizationRef).binding().equals(expected.binding())) {
            throw unavailable();
        }
    }

    private static ApiErrorException unavailable() {
        return new ApiErrorException(HttpStatus.SERVICE_UNAVAILABLE, "calendar-provider-unavailable",
                "The Calendar provider or identity mapping is unavailable.",
                Map.of("module", "calendar", "diagnosticsRedacted", true));
    }

    public record Bound(ProviderBinding binding, CalendarProviderPort provider) {}
}
