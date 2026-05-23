package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.service.calendar.CalDavCalendarAdapter;
import com.massimotter.weave.backend.service.calendar.CalendarAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CalendarCalDavConfiguration {

    @Bean
    CalendarAdapter calendarAdapter(CalendarCalDavProperties properties) {
        return new CalDavCalendarAdapter(properties);
    }
}
