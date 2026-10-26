package com.massimotter.weave.backend.boards.home;

import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class BoardsHomeConfiguration {
    @Bean
    WorkspaceHomeContribution boardsHomeContribution() {
        return new WorkspaceHomeContribution("boards", List.of(new WorkspaceHomeContribution.Section("open-tasks", "Open tasks", "Tasks", "weave://home/tasks", 1)));
    }
}
