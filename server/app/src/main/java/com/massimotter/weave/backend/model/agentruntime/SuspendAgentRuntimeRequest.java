package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SuspendAgentRuntimeRequest(
        @NotBlank @Size(max = 500) String reason) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown Agent Runtime suspension property");
    }
}
