package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Member-safe Weave Chat readiness and policy state.")
public record ChatReadinessResponse(
        @Schema(description = "Stable member impact state.", allowableValues = {"usable", "disabled", "degraded", "policy-blocked"}, example = "usable")
        String impactState,
        @Schema(description = "Support-safe member impact explanation.")
        String memberImpact,
        @Schema(description = "Capabilities granted to the current principal for this domain.")
        List<String> grantedCapabilities,
        @Schema(description = "Whether provider diagnostics have been intentionally redacted from this member response.", example = "true")
        boolean diagnosticsRedacted) {
}
