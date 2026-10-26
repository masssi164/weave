package com.massimotter.weave.backend.integrations.discovery;

import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class IntegrationsDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment integrationsDocumentsFragment() {
        return IntegrationsDiscoveryMetadata.documentsFragment();
    }
}
