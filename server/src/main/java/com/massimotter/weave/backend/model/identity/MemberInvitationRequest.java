package com.massimotter.weave.backend.model.identity;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberInvitationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 200) String displayName,
        @NotBlank @Pattern(regexp = "owner|admin|member|guest") String role) {

    @JsonAnySetter
    public void rejectUnknownProperty(String name, Object value) {
        throw new IllegalArgumentException(
                "Unknown member invitation property; send one canonical role only");
    }
}
