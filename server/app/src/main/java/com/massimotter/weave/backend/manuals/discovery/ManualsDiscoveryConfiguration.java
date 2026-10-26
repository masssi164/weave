package com.massimotter.weave.backend.manuals.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ManualsDiscoveryConfiguration {

    @Bean
    AdminCapabilityDeclaration manualsAdminCapabilities() {
        return ManualsAdminMetadata.capabilities();
    }

    @Bean
    ProviderCategoryFragment manualsManualsHelpCategory() {
        return ManualsDiscoveryMetadata.manualsHelpCategory();
    }
}
