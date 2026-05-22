package com.massimotter.weave.backend.devops.support;

import com.massimotter.weave.backend.devops.port.CiProvider;
import com.massimotter.weave.backend.devops.port.IssueTrackerProvider;
import com.massimotter.weave.backend.devops.port.ReleaseProvider;
import com.massimotter.weave.backend.devops.port.SourceControlProvider;
import com.massimotter.weave.backend.model.devops.DevopsIssueSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsMergeRequestSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsPipelineSummaryResponse;
import com.massimotter.weave.backend.model.devops.DevopsReleaseSummaryResponse;
import com.massimotter.weave.backend.model.devops.LinkedSourceProjectResponse;
import com.massimotter.weave.backend.model.devops.SourceRepositoryResponse;
import com.massimotter.weave.backend.provider.ProviderModule;
import com.massimotter.weave.backend.provider.ProviderState;
import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DisabledDevopsProvider implements SourceControlProvider, IssueTrackerProvider, CiProvider, ReleaseProvider {

    private final ProviderStatusResponse status;

    private DisabledDevopsProvider(ProviderModule module, String providerKey, Set<String> supportedCapabilities) {
        this.status = new ProviderStatusResponse(
                module,
                providerKey,
                ProviderState.NOT_CONFIGURED,
                "not_configured",
                false,
                false,
                true,
                true,
                true,
                false,
                providerKey + " DevOps provider is represented but no adapter credentials/runtime are configured.",
                supportedCapabilities,
                Set.of(
                        "provider-writes",
                        "premium-ultimate-only-features",
                        "repository-secret-exposure",
                        "ci-variable-exposure",
                        "raw-provider-errors"),
                List.of("devops-provider-not-configured", "devops-provider-disabled", "devops-provider-unavailable"),
                "support-safe: no tokens, deploy keys, webhook secrets, CI variables, repository secrets, clone credentials, or raw provider errors",
                List.of("gitlab-ce-foss", "forgejo"),
                Map.of(
                        "primaryProvider", "gitlab-ce-foss",
                        "alternativeProvider", "forgejo",
                        "readOnly", true,
                        "paidFeaturesRequired", false));
    }

    public static DisabledDevopsProvider gitlab(ProviderModule module, Set<String> capabilities) {
        return new DisabledDevopsProvider(module, "gitlab-ce-foss", capabilities);
    }

    public static DisabledDevopsProvider forgejo(ProviderModule module, Set<String> capabilities) {
        return new DisabledDevopsProvider(module, "forgejo", capabilities);
    }

    @Override
    public ProviderStatusResponse status() {
        return status;
    }

    @Override
    public List<LinkedSourceProjectResponse> linkedProjects(String workspaceId, String channelId) {
        return List.of();
    }

    @Override
    public List<SourceRepositoryResponse> repositories(String workspaceId, String channelId) {
        return List.of();
    }

    @Override
    public List<DevopsMergeRequestSummaryResponse> mergeRequests(String workspaceId, String channelId) {
        return List.of();
    }

    @Override
    public List<DevopsIssueSummaryResponse> openIssues(String workspaceId, String channelId) {
        return List.of();
    }

    @Override
    public List<DevopsPipelineSummaryResponse> latestPipelines(String workspaceId, String channelId) {
        return List.of();
    }

    @Override
    public List<DevopsReleaseSummaryResponse> releases(String workspaceId, String channelId) {
        return List.of();
    }
}
