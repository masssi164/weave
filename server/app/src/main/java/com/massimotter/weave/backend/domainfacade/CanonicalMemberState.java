package com.massimotter.weave.backend.domainfacade;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable member-visible state for canonical Weave domain facades.")
public enum CanonicalMemberState {
    READY("available"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("disabled_by_policy"),
    UNAVAILABLE("unavailable"),
    MISCONFIGURED("not_configured"),
    UNSUPPORTED("coming_later");

    private final String value;

    CanonicalMemberState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
