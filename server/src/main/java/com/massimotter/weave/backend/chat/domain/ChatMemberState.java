package com.massimotter.weave.backend.chat.domain;

import com.fasterxml.jackson.annotation.JsonValue;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Stable product-level Chat state returned to member clients.")
public enum ChatMemberState {
    READY("ready"),
    DISABLED("disabled"),
    DEGRADED("degraded"),
    POLICY_BLOCKED("policy_blocked"),
    UNAVAILABLE("unavailable"),
    MISCONFIGURED("misconfigured");

    private final String value;

    ChatMemberState(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }
}
