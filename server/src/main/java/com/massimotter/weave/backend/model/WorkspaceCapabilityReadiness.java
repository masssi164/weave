package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Readiness states returned for workspace capabilities.")
public enum WorkspaceCapabilityReadiness {
    READY("ready"),
    DEGRADED("degraded"),
    BLOCKED("blocked"),
    UNAVAILABLE("unavailable");

    private final String value;

    WorkspaceCapabilityReadiness(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
