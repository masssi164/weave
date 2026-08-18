package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.service.calendar.CalDavCalendarAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarCalDavConfiguration {

    @Bean
    @ConditionalOnProperty(name = "weave.calendar.provider", havingValue = "nextcloud-caldav")
    CalendarProviderPort calendarProviderPort(CalendarCalDavProperties properties) {
        return new CalDavCalendarAdapter(properties);
    }
}
