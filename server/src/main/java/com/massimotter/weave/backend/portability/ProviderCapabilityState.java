package com.massimotter.weave.backend.portability;

import com.fasterxml.jackson.annotation.JsonValue;

public enum ProviderCapabilityState {
    AVAILABLE("available"),
    DEGRADED("degraded"),
    UNAVAILABLE("unavailable");

    private final String value;

    ProviderCapabilityState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
