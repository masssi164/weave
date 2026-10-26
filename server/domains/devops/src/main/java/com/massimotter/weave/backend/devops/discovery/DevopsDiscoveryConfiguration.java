package com.massimotter.weave.backend.devops.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DevopsDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment devopsReleaseEvidenceCategory() {
        return DevopsDiscoveryMetadata.releaseEvidenceCategory();
    }
}
