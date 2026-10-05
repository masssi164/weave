package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import java.time.Instant;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CalendarBindingBootstrapConfiguration.Properties.class)
public class CalendarBindingBootstrapConfiguration {
    @ConfigurationProperties("weave.provider-bindings.bootstrap.calendar")
    public record Properties(boolean enabled, String organizationRef, String adapterKey, String configurationRef) {}

    @Bean
    @ConditionalOnProperty(name = "weave.provider-bindings.bootstrap.calendar.enabled", havingValue = "true")
    ApplicationRunner calendarBindingBootstrap(ProviderBindingRepository repository, Properties properties,
            ContextAuthorizationProperties context) {
        return args -> {
            if (!context.defaultTenantId().equals(properties.organizationRef())
                    || properties.adapterKey() == null || properties.adapterKey().isBlank()
                    || !CalendarUserApiService.CONFIGURATION_REF.equals(properties.configurationRef())) {
                throw new IllegalStateException("Calendar binding bootstrap configuration is incomplete or belongs to another organization");
            }
            var current = repository.current(properties.organizationRef(), "calendar");
            if (current.isPresent()) {
                var binding = current.orElseThrow();
                if (!binding.adapterKey().equals(properties.adapterKey()) || !binding.configurationRef().equals(properties.configurationRef())) {
                    throw new IllegalStateException("Calendar binding bootstrap conflicts with an existing authority");
                }
                return;
            }
            repository.activate(properties.organizationRef(), "calendar", 0, properties.adapterKey(), properties.configurationRef(), Instant.now());
        };
    }
}
