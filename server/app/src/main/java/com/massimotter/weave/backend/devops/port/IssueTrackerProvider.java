package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.model.devops.DevopsIssueSummaryResponse;
import com.massimotter.weave.backend.provider.ProviderPort;
import java.util.List;

public interface IssueTrackerProvider extends ProviderPort {
    List<DevopsIssueSummaryResponse> openIssues(String workspaceId, String channelId);
}
