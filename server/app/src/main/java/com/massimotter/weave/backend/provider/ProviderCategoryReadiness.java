package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Admin Workspace Health readiness state for one provider-neutral category.")
public enum ProviderCategoryReadiness {
    READY("ready"),
    DISABLED("disabled"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("policy_blocked"),
    MISCONFIGURED("misconfigured");

    private final String value;

    ProviderCategoryReadiness(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
