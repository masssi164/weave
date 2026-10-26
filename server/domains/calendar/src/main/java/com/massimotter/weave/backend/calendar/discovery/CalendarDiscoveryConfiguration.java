package com.massimotter.weave.backend.calendar.discovery;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import com.massimotter.weave.backend.admin.domain.SuiteDomainDeclaration;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CalendarDiscoveryConfiguration {

    @Bean
    SuiteDomainDeclaration calendarAdminSuite() {
        return CalendarAdminMetadata.suite();
    }

    @Bean
    AdminCapabilityDeclaration calendarAdminCapabilities() {
        return CalendarAdminMetadata.capabilities();
    }

    @Bean
    CalendarPlatformDiagnostics calendarPlatformDiagnostics(
            WorkspaceCapabilityService capabilities) {
        return new CalendarPlatformDiagnostics(capabilities);
    }

    @Bean
    ProviderCategoryFragment calendarCalendarCategory() {
        return CalendarDiscoveryMetadata.calendarCategory();
    }

    @Bean
    CanonicalDomainContribution calendarCalendarDomain() {
        return CalendarDiscoveryMetadata.calendarDomain();
    }
}
