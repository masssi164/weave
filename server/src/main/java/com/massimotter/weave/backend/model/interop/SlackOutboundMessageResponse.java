package com.massimotter.weave.backend.model.interop;

import java.util.Map;

public record SlackOutboundMessageResponse(
        String idempotencyKey,
        String provider,
        String workspaceRef,
        String channelRef,
        String deliveryStatus,
        boolean dryRunOnly,
        boolean productionCallAttempted,
        Map<String, Object> payload) {
}
