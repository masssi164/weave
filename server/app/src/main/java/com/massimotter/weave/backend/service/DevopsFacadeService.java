package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.devops.port.CiProvider;
import com.massimotter.weave.backend.devops.port.IssueTrackerProvider;
import com.massimotter.weave.backend.devops.port.ReleaseProvider;
import com.massimotter.weave.backend.devops.port.SourceControlProvider;
import com.massimotter.weave.backend.model.devops.DevopsIssueSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsMergeRequestSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsPipelineSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsReleaseSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsSummaryResponse;
import com.massimotter.weave.backend.model.devops.LinkedSourceProjectResponse;
import com.massimotter.weave.backend.model.devops.SourceRepositoryResponse;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DevopsFacadeService {

    private final List<SourceControlProvider> sourceControlProviders;
    private final List<IssueTrackerProvider> issueTrackerProviders;
    private final List<CiProvider> ciProviders;
    private final List<ReleaseProvider> releaseProviders;

    public DevopsFacadeService(
            List<SourceControlProvider> sourceControlProviders,
            List<IssueTrackerProvider> issueTrackerProviders,
            List<CiProvider> ciProviders,
            List<ReleaseProvider> releaseProviders) {
        this.sourceControlProviders = sourceControlProviders == null ? List.of() : List.copyOf(sourceControlProviders);
        this.issueTrackerProviders = issueTrackerProviders == null ? List.of() : List.copyOf(issueTrackerProviders);
        this.ciProviders = ciProviders == null ? List.of() : List.copyOf(ciProviders);
        this.releaseProviders = releaseProviders == null ? List.of() : List.copyOf(releaseProviders);
    }

    public DevopsSummaryResponse summary(String workspaceId, String channelId) {
        List<ProviderStatusResponse> readiness = new ArrayList<>();
        List<LinkedSourceProjectResponse> linkedProjects = new ArrayList<>();
        List<SourceRepositoryResponse> repositories = new ArrayList<>();
        List<DevopsMergeRequestSummaryResponse> mergeRequests = new ArrayList<>();
        for (SourceControlProvider provider : sourceControlProviders) {
            readiness.add(provider.status());
            linkedProjects.addAll(provider.linkedProjects(workspaceId, channelId));
            repositories.addAll(provider.repositories(workspaceId, channelId));
            mergeRequests.addAll(provider.mergeRequests(workspaceId, channelId));
        }

        List<DevopsIssueSummaryResponse> issues = new ArrayList<>();
        for (IssueTrackerProvider provider : issueTrackerProviders) {
            readiness.add(provider.status());
            issues.addAll(provider.openIssues(workspaceId, channelId));
        }

        List<DevopsPipelineSummaryResponse> pipelines = new ArrayList<>();
        for (CiProvider provider : ciProviders) {
            readiness.add(provider.status());
            pipelines.addAll(provider.latestPipelines(workspaceId, channelId));
        }

        List<DevopsReleaseSummaryResponse> releases = new ArrayList<>();
        for (ReleaseProvider provider : releaseProviders) {
            readiness.add(provider.status());
            releases.addAll(provider.releases(workspaceId, channelId));
        }

        return new DevopsSummaryResponse(
                workspaceId,
                channelId,
                "provider-neutral-read-only-contract",
                true,
                false,
                readiness.stream().allMatch(ProviderStatusResponse::supportSafe),
                readiness,
                linkedProjects,
                repositories,
                issues,
                mergeRequests,
                pipelines,
                releases);
    }
}
