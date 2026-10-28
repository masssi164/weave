package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.devops.api.DevopsPipelineSummaryResponse;
import com.massimotter.weave.backend.discovery.provider.ProviderPort;
import java.util.List;

public interface CiProvider extends ProviderPort {
    List<DevopsPipelineSummaryResponse> latestPipelines(String workspaceId, String channelId);
}
