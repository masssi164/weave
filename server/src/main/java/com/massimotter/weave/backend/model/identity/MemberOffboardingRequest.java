package com.massimotter.weave.backend.model.identity;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public record MemberOffboardingRequest(
    @NotNull @Pattern(regexp = "OFFBOARD_MEMBER") String confirmation) {}
