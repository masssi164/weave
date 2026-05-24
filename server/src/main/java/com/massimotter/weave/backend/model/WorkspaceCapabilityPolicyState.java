package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support-safe policy state for an effective workspace capability.")
public enum WorkspaceCapabilityPolicyState {
    ALLOWED("allowed"),
    POLICY_BLOCKED("policy_blocked"),
    DISABLED("disabled"),
    UNAVAILABLE("unavailable");

    private final String value;

    WorkspaceCapabilityPolicyState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
