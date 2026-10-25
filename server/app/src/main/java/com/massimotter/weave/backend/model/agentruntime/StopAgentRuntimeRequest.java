package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record StopAgentRuntimeRequest(
        @Pattern(regexp = "graceful|force-after-timeout") String mode,
        @Min(1) @Max(300) Integer timeoutSeconds) {

    public StopAgentRuntimeRequest {
        mode = mode == null || mode.isBlank() ? "graceful" : mode;
        if ("graceful".equals(mode) && timeoutSeconds != null) {
            throw new IllegalArgumentException("timeoutSeconds is accepted only with force-after-timeout");
        }
        if ("force-after-timeout".equals(mode) && timeoutSeconds == null) {
            throw new IllegalArgumentException("force-after-timeout requires timeoutSeconds");
        }
    }

    public static StopAgentRuntimeRequest graceful() {
        return new StopAgentRuntimeRequest("graceful", null);
    }

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown Agent Runtime stop property");
    }
}
