package com.massimotter.weave.backend.calendar.discovery;

import com.massimotter.weave.backend.discovery.catalog.CanonicalDomainContribution;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryFragment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CalendarDiscoveryConfiguration {

    @Bean
    ProviderCategoryFragment calendarCalendarCategory() {
        return CalendarDiscoveryMetadata.calendarCategory();
    }

    @Bean
    CanonicalDomainContribution calendarCalendarDomain() {
        return CalendarDiscoveryMetadata.calendarDomain();
    }
}
