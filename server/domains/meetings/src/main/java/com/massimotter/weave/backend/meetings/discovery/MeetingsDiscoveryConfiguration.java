package com.massimotter.weave.backend.meetings.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class MeetingsDiscoveryConfiguration {

    @Bean
    SuiteDomainDeclaration meetingsAdminSuite() {
        return MeetingsAdminMetadata.suite();
    }

    @Bean
    AdminCapabilityDeclaration meetingsAdminCapabilities() {
        return MeetingsAdminMetadata.capabilities();
    }

    @Bean
    ProviderCategoryFragment meetingsMeetingsCallsCategory() {
        return MeetingsDiscoveryMetadata.meetingsCallsCategory();
    }

    @Bean
    CanonicalDomainContribution meetingsCallsDomain() {
        return MeetingsDiscoveryMetadata.callsDomain();
    }
}
