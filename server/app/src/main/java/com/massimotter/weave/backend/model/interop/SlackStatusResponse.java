package com.massimotter.weave.backend.model.interop;

import java.util.List;

public record SlackStatusResponse(
        boolean enabled,
        String status,
        String readiness,
        boolean oauthConfigured,
        boolean signingConfigured,
        boolean tokenReferenceConfigured,
        boolean channelMappingConfigured,
        boolean productionCallsAllowed,
        List<String> degradedStates,
        List<String> actions) {
}
