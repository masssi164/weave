package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One next action shown on Weave Home.")
public record WorkspaceHomeActionResponse(
        @Schema(description = "Stable action key.", example = "review-workspace-health")
        String key,
        @Schema(description = "Support-safe user-facing label.", example = "Review workspace health")
        String label,
        @Schema(description = "Backend-owned product route/surface, not a raw provider URL.", example = "weave://settings/workspace")
        String productRoute,
        @Schema(description = "Why the action is shown.")
        String reason) {
}
