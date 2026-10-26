package com.massimotter.weave.backend.discovery.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CoreDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment coreAdminControlPlaneCategory() {
        return CoreDiscoveryMetadata.adminControlPlaneCategory();
    }

    @Bean
    CanonicalDomainContribution corePeopleDomain() {
        return CoreDiscoveryMetadata.peopleDomain();
    }

    @Bean
    CanonicalDomainContribution coreSpacesDomain() {
        return CoreDiscoveryMetadata.spacesDomain();
    }

    @Bean
    CanonicalDomainContribution coreHealthDomain() {
        return CoreDiscoveryMetadata.healthDomain();
    }
}
