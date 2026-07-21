package com.massimotter.weave.mcp;

import java.time.Instant;
import java.util.Set;

record McpBackendContext(
        String authorizationRef,
        String organizationRef,
        String cellRef,
        String workloadClientId,
        String workloadRefHash,
        String runtimeProfileId,
        String runtimeProfileHash,
        String entitlementRevision,
        Instant authorizationExpiresAt,
        Set<String> grantedScopes,
        Set<String> visibleToolClasses) {

    McpBackendContext {
        grantedScopes = Set.copyOf(grantedScopes);
        visibleToolClasses = Set.copyOf(visibleToolClasses);
    }
}
