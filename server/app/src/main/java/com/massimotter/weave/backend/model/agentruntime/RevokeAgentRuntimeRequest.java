package com.massimotter.weave.backend.model.agentruntime;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RevokeAgentRuntimeRequest(
        @NotBlank @Size(max = 500) String reason,
        @NotBlank @Pattern(regexp = "sha256:[a-f0-9]{64}") String entitlementRevision) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException("Unknown Agent Runtime revocation property");
    }
}
