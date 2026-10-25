package com.massimotter.weave.backend.files.config;

import com.massimotter.weave.backend.config.FilesRuntimeProperties;
import com.massimotter.weave.backend.files.port.FilesProviderPort;
import com.massimotter.weave.backend.health.ProviderHealthContribution;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class FilesProviderHealthConfiguration {
    @Bean
    ProviderHealthContribution filesProviderHealth(ObjectProvider<FilesProviderPort> providers, FilesRuntimeProperties properties) {
        FilesProviderPort provider = providers.orderedStream()
                .filter(candidate -> properties.provider().equals(candidate.conformanceProfile().adapterKey()))
                .findFirst().orElse(null);
        return new ProviderHealthContribution("files", () -> provider != null && provider.configured(),
                () -> provider == null ? ProviderCapabilityProbeResult.unavailable("files-adapter-unavailable") : provider.healthProbe());
    }
}
