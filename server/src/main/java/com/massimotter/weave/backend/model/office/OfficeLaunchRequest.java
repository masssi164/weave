package com.massimotter.weave.backend.model.office;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record OfficeLaunchRequest(
        @NotBlank @Size(max = 256) String fileId,
        @NotBlank @Size(max = 32) @Pattern(regexp = "view|edit|comment|review|form-fill") String requestedMode) {
}
