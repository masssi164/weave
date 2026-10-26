package com.massimotter.weave.backend.devops.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DevopsDiscoveryConfiguration {

    @Bean
    AdminCapabilityDeclaration devopsAdminCapabilities() {
        return DevopsAdminMetadata.capabilities();
    }

    @Bean
    ProviderCategoryFragment devopsReleaseEvidenceCategory() {
        return DevopsDiscoveryMetadata.releaseEvidenceCategory();
    }
}
