package com.massimotter.weave.core.autoconfigure;

import com.massimotter.weave.backend.health.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.health.ProviderHealthContribution;
import com.massimotter.weave.backend.health.config.ProviderHealthProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/** Shared serving cache; domain probes remain lazy contributions from their owners. */
@AutoConfiguration(afterName = "org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(ProviderHealthProperties.class)
public class CoreProviderHealthAutoConfiguration {
    @Bean
    ProviderCapabilityHealthService providerCapabilityHealthService(
            List<ProviderHealthContribution> contributions,
            ProviderHealthProperties properties,
            MeterRegistry registry) {
        return new ProviderCapabilityHealthService(contributions, properties, registry);
    }
}
