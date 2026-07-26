package com.massimotter.weave.backend.model.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record MemberInvitationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 200) String displayName,
        @NotBlank @Pattern(regexp = "owner|admin|member|guest") String role,
        @Size(max = 32)
                List<
                                @NotBlank
                                @Size(max = 160)
                                @Pattern(regexp = "[a-z][a-z0-9-]*(?:[._:-][a-z0-9-]+)*")
                                String>
                        capabilities) {

    public MemberInvitationRequest {
        capabilities =
                capabilities == null
                        ? List.of()
                        : capabilities.stream().map(String::trim).distinct().sorted().toList();
    }
}
