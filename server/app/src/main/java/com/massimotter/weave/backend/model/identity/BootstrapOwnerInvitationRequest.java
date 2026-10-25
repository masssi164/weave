package com.massimotter.weave.backend.model.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record BootstrapOwnerInvitationRequest(
    @NotBlank @Email @Size(max = 320) String email,
    @Size(max = 200) String displayName) {}
