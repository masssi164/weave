package com.massimotter.weave.backend.model.devops;

import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.List;

public record DevopsSummaryResponse(
        String workspaceId,
        String channelId,
        String releaseStatus,
        boolean readOnly,
        boolean paidFeaturesRequired,
        boolean supportSafe,
        List<ProviderStatusResponse> providerReadiness,
        List<LinkedSourceProjectResponse> linkedProjects,
        List<SourceRepositoryResponse> repositories,
        List<DevopsIssueSummaryResponse> openIssues,
        List<DevopsMergeRequestSummaryResponse> mergeRequests,
        List<DevopsPipelineSummaryResponse> pipelines,
        List<DevopsReleaseSummaryResponse> releases) {

    public DevopsSummaryResponse {
        providerReadiness = providerReadiness == null ? List.of() : List.copyOf(providerReadiness);
        linkedProjects = linkedProjects == null ? List.of() : List.copyOf(linkedProjects);
        repositories = repositories == null ? List.of() : List.copyOf(repositories);
        openIssues = openIssues == null ? List.of() : List.copyOf(openIssues);
        mergeRequests = mergeRequests == null ? List.of() : List.copyOf(mergeRequests);
        pipelines = pipelines == null ? List.of() : List.copyOf(pipelines);
        releases = releases == null ? List.of() : List.copyOf(releases);
    }
}
