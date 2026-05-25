package com.massimotter.weave.backend.domainfacade;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable member-visible state for canonical Weave domain facades.")
public enum CanonicalMemberState {
    READY("ready"),
    DISABLED("disabled"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("policy_blocked"),
    UNAVAILABLE("unavailable"),
    MISCONFIGURED("misconfigured"),
    UNSUPPORTED("unsupported");

    private final String value;

    CanonicalMemberState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
