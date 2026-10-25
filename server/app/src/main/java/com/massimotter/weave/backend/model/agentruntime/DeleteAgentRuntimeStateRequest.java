package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DeleteAgentRuntimeStateRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Pattern(regexp = "DELETE_RUNTIME_STATE_ONLY") String confirmation) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown Agent Runtime state-deletion property");
    }
}
