package com.massimotter.weave.backend.devops.port;

import com.massimotter.weave.backend.model.devops.DevopsReleaseSummaryResponse;
import com.massimotter.weave.backend.provider.ProviderPort;
import java.util.List;

public interface ReleaseProvider extends ProviderPort {
    List<DevopsReleaseSummaryResponse> releases(String workspaceId, String channelId);
}
