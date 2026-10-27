package com.massimotter.weave.backend.identity.member.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrganizationMemberUpdateRequest(
    @NotBlank @Pattern(regexp = "owner|admin|member|guest") String role,
    boolean enabled) {}
