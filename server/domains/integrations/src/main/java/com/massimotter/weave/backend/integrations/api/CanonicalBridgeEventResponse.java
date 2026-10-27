package com.massimotter.weave.backend.integrations.api;

import java.util.Map;

public record CanonicalBridgeEventResponse(
        String idempotencyKey,
        String provider,
        String type,
        String direction,
        String workspaceRef,
        String roomRef,
        String actorRef,
        Map<String, Object> payload,
        boolean dryRunOnly) {
}
