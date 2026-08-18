package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/** Closed, support-safe error contract for Agent Runtime Control routes. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AgentRuntimeErrorEnvelope(
        String code,
        String capabilityState,
        boolean retryable,
        String userMessage,
        String auditRef,
        Map<String, Object> supportDetail) {

    public AgentRuntimeErrorEnvelope {
        requireText(code, "code");
        requireText(capabilityState, "capabilityState");
        requireText(userMessage, "userMessage");
        requireText(auditRef, "auditRef");
        supportDetail = supportDetail == null || supportDetail.isEmpty()
                ? null
                : Map.copyOf(supportDetail);
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
