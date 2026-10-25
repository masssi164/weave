package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support-safe explanation for one denied effective capability.")
public record EffectivePolicyDenyResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String capability,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String reason,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String source) {
}
