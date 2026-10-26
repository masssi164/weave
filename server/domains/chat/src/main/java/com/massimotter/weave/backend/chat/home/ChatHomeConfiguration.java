package com.massimotter.weave.backend.chat.home;

import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ChatHomeConfiguration {
    @Bean
    WorkspaceHomeContribution chatHomeContribution() {
        return new WorkspaceHomeContribution("chat", List.of(new WorkspaceHomeContribution.Section("recent-channels", "Recent channels", "Chat", "weave://home/channels", 0)));
    }
}
