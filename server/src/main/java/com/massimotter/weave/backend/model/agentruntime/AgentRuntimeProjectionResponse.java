package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRuntimeProjectionResponse(
        String personRef,
        String cellRef,
        String runtimeProvider,
        String entitlementState,
        String entitlementRevision,
        String desiredState,
        String observedState,
        String runtimeProfileRef,
        String workspaceRevision,
        Instant lastWakeAt,
        Instant lastSyncAt,
        int conflicts,
        String capabilityState,
        String auditRef) {
}
