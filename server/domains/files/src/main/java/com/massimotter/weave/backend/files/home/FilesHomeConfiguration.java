package com.massimotter.weave.backend.files.home;

import com.massimotter.weave.backend.home.domain.WorkspaceHomeContribution;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(FilesHomeRecentActivityService.class)
public class FilesHomeConfiguration {
    @Bean
    WorkspaceHomeContribution filesHomeContribution() {
        return new WorkspaceHomeContribution("files", List.of());
    }
}
