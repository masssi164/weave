package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@Schema(description = "Member-safe Weave Chat readiness and policy state.")
public record ChatReadinessResponse(
        @Schema(description = "Stable member impact state.", allowableValues = {"available", "disabled_by_policy", "not_configured", "degraded", "unavailable", "coming_later"}, example = "available",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String impactState,
        @Schema(description = "Support-safe member impact explanation.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String memberImpact,
        @Schema(description = "Capabilities granted to the current principal for this domain.",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        List<String> grantedCapabilities,
        @Schema(description = "Whether provider diagnostics have been intentionally redacted from this member response.", example = "true",
                requiredMode = Schema.RequiredMode.REQUIRED)
        boolean diagnosticsRedacted) {
}
