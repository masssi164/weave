package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.model.devops.DevopsPipelineSummaryResponse;
import com.massimotter.weave.backend.provider.ProviderPort;
import java.util.List;

public interface CiProvider extends ProviderPort {
    List<DevopsPipelineSummaryResponse> latestPipelines(String workspaceId, String channelId);
}
