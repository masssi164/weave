package com.massimotter.weave.backend.calendar.home;

import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CalendarHomeConfiguration {
    @Bean
    WorkspaceHomeContribution calendarHomeContribution() {
        return new WorkspaceHomeContribution("calendar", List.of(new WorkspaceHomeContribution.Section("upcoming-meetings", "Upcoming meetings", "Calendar", "weave://home/meetings", 2)));
    }
}
