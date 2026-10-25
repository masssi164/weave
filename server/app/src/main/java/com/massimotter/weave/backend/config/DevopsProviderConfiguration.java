package com.massimotter.weave.backend.config;

import com.massimotter.weave.backend.devops.port.CiProvider;
import com.massimotter.weave.backend.devops.port.IssueTrackerProvider;
import com.massimotter.weave.backend.devops.port.ReleaseProvider;
import com.massimotter.weave.backend.devops.port.SourceControlProvider;
import com.massimotter.weave.backend.devops.support.DisabledDevopsProvider;
import com.massimotter.weave.backend.provider.ProviderModule;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DevopsProviderConfiguration {

    @Bean
    SourceControlProvider gitlabSourceControlProvider() {
        return DisabledDevopsProvider.gitlab(ProviderModule.SOURCE_CONTROL, Set.of("linked-projects", "repository-list", "merge-request-summary"));
    }

    @Bean
    IssueTrackerProvider gitlabIssueTrackerProvider() {
        return DisabledDevopsProvider.gitlab(ProviderModule.ISSUE_TRACKER, Set.of("open-issues", "labels", "assignees"));
    }

    @Bean
    CiProvider gitlabCiProvider() {
        return DisabledDevopsProvider.gitlab(ProviderModule.CI, Set.of("latest-pipeline", "job-summary", "commit-ref-status"));
    }

    @Bean
    ReleaseProvider gitlabReleaseProvider() {
        return DisabledDevopsProvider.gitlab(ProviderModule.RELEASE, Set.of("release-list", "tag-list"));
    }

}
