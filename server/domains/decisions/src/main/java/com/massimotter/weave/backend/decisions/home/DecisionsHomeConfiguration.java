package com.massimotter.weave.backend.decisions.home;

import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DecisionsHomeConfiguration {
    @Bean
    WorkspaceHomeContribution decisionsHomeContribution() {
        return new WorkspaceHomeContribution("decisions", List.of(new WorkspaceHomeContribution.Section("recent-decisions", "Recent decisions", "Decisions", "weave://home/decisions", 3)));
    }
}
