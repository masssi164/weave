package com.massimotter.weave.backend.model.guest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public record GuestInvitationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @Size(max = 128) String displayName,
        List<String> capabilities) {
}
