package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Provider-neutral member capability states exposed by organization capability manifests.")
public enum CapabilityManifestState {
    AVAILABLE("available"),
    DISABLED_BY_POLICY("disabled_by_policy"),
    NOT_CONFIGURED("not_configured"),
    DEGRADED("degraded"),
    UNAVAILABLE("unavailable"),
    COMING_LATER("coming_later");

    private final String value;

    CapabilityManifestState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
