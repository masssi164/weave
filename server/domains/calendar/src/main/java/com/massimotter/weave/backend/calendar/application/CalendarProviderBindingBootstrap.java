package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.calendar.config.CalendarBindingBootstrapConfiguration.Properties;
import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import java.time.Instant;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

/** Validates the configured Calendar adapter before installing its initial binding authority. */
public final class CalendarProviderBindingBootstrap implements ApplicationRunner {
    private final ProviderBindingRepository repository;
    private final Properties properties;
    private final ContextAuthorizationProperties context;
    private final CalendarProviderBindingReadiness readiness;

    public CalendarProviderBindingBootstrap(
            ProviderBindingRepository repository, Properties properties,
            ContextAuthorizationProperties context, CalendarProviderBindingReadiness readiness) {
        this.repository = repository;
        this.properties = properties;
        this.context = context;
        this.readiness = readiness;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!context.defaultTenantId().equals(properties.organizationRef())
                || properties.adapterKey() == null || properties.adapterKey().isBlank()
                || !CalendarUserApiService.CONFIGURATION_REF.equals(properties.configurationRef())) {
            throw new IllegalStateException("Calendar binding bootstrap configuration is incomplete or belongs to another organization");
        }
        readiness.requireConfiguredAdapter(properties.adapterKey());
        var current = repository.current(properties.organizationRef(), "calendar");
        if (current.isPresent()) {
            var binding = current.orElseThrow();
            if (binding.state() != ProviderBinding.State.ACTIVE
                    || !properties.organizationRef().equals(binding.organizationRef())
                    || !"calendar".equals(binding.domain())
                    || !binding.adapterKey().equals(properties.adapterKey())
                    || !binding.configurationRef().equals(properties.configurationRef())) {
                throw new IllegalStateException("Calendar binding bootstrap conflicts with an existing authority");
            }
            return;
        }
        repository.activate(properties.organizationRef(), "calendar", 0, properties.adapterKey(), properties.configurationRef(), Instant.now());
    }
}
