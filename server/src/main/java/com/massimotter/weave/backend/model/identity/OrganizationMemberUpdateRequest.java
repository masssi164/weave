package com.massimotter.weave.backend.model.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record OrganizationMemberUpdateRequest(
    @NotBlank @Pattern(regexp = "owner|admin|member|guest") String role,
    boolean agentRuntimeEntitled,
    boolean enabled) {}
