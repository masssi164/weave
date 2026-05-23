package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One operator-facing workspace readiness check.")
public record WorkspaceReleaseReadinessCheckResponse(
        @Schema(description = "Stable check identifier.", example = "chat")
        String key,
        @Schema(description = "Human-readable check label.", example = "Matrix chat route")
        String label,
        @Schema(description = "Current readiness state for this check.")
        WorkspaceCapabilityReadiness readiness,
        @Schema(description = "Why this check is in its current state.")
        String message,
        @Schema(description = "Recommended operator action when the check is not ready.")
        String action) {
}
