package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Availability and readiness state for one workspace capability.")
public record WorkspaceCapabilityStatusResponse(
        @Schema(description = "Whether the capability is enabled for this workspace.", example = "true")
        boolean enabled,
        @Schema(description = "Current readiness state for this capability.")
        WorkspaceCapabilityReadiness readiness) {
}
