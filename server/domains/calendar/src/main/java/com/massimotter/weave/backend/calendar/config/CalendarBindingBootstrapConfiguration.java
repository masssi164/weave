package com.massimotter.weave.backend.calendar.config;

import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.calendar.application.CalendarProviderBindingReadiness;
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
            ContextAuthorizationProperties context, CalendarProviderBindingReadiness readiness) {
        return new com.massimotter.weave.backend.calendar.application.CalendarProviderBindingBootstrap(
                repository, properties, context, readiness);
    }
}
