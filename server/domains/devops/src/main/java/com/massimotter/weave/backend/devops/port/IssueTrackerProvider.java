package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.devops.api.DevopsIssueSummaryResponse;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import java.util.List;

public interface IssueTrackerProvider extends ProviderPort {
    List<DevopsIssueSummaryResponse> openIssues(String workspaceId, String channelId);
}
