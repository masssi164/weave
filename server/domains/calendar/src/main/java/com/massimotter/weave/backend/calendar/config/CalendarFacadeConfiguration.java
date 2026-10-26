package com.massimotter.weave.backend.calendar.config;

import com.massimotter.weave.backend.calendar.application.CalendarClientSetupService;
import com.massimotter.weave.backend.calendar.application.CalendarDavSyncService;
import com.massimotter.weave.backend.calendar.application.CalendarFacadeAuthorization;
import com.massimotter.weave.backend.calendar.provider.caldav.AppleMobileConfigProfileRenderer;
import com.massimotter.weave.backend.calendar.provider.icalendar.IcalendarMapper;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(CalendarClientSetupProperties.class)
@Import({CalendarFacadeAuthorization.class, CalendarClientSetupService.class, CalendarDavSyncService.class})
public class CalendarFacadeConfiguration {
    @Bean
    IcalendarMapper calendarFacadeIcalendarMapper() {
        return new IcalendarMapper();
    }

    @Bean
    AppleMobileConfigProfileRenderer calendarAppleProfileRenderer(
            CalendarClientSetupProperties properties) {
        return new AppleMobileConfigProfileRenderer(properties.publicBaseUrl());
    }

    @Bean
    Clock calendarFacadeClock() {
        return Clock.systemUTC();
    }
}
