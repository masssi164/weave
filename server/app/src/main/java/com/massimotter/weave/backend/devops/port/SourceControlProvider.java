package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.model.devops.DevopsMergeRequestSummaryResponse;
import com.massimotter.weave.backend.model.devops.LinkedSourceProjectResponse;
import com.massimotter.weave.backend.model.devops.SourceRepositoryResponse;
import com.massimotter.weave.backend.provider.ProviderPort;
import java.util.List;

public interface SourceControlProvider extends ProviderPort {
    List<LinkedSourceProjectResponse> linkedProjects(String workspaceId, String channelId);

    List<SourceRepositoryResponse> repositories(String workspaceId, String channelId);

    List<DevopsMergeRequestSummaryResponse> mergeRequests(String workspaceId, String channelId);
}
