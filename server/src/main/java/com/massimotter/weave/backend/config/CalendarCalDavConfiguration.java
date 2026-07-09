package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.service.calendar.CalDavCalendarAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarCalDavConfiguration {

    @Bean
    CalendarProviderPort calendarProviderPort(CalendarCalDavProperties properties) {
        return new CalDavCalendarAdapter(properties);
    }
}
