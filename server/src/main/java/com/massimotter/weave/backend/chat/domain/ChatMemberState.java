package com.massimotter.weave.backend.chat.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable product-level Chat state returned to member clients.")
public enum ChatMemberState {
    READY("available"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("disabled_by_policy"),
    UNAVAILABLE("unavailable"),
    MISCONFIGURED("not_configured"),
    COMING_LATER("coming_later");

    private final String value;

    ChatMemberState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
