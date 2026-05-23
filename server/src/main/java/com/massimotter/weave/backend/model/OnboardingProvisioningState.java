package com.massimotter.weave.backend.model;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Provisioning states exposed to first-run users for downstream modules.")
public enum OnboardingProvisioningState {
    NOT_CONFIGURED("not_configured"),
    PENDING("pending"),
    READY("ready"),
    DEGRADED("degraded"),
    FAILED("failed");

    private final String value;

    OnboardingProvisioningState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
