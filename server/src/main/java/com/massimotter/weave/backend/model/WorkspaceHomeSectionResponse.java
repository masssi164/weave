package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "One support-safe Weave Home section summary.")
public record WorkspaceHomeSectionResponse(
        @Schema(description = "Stable product section key.", example = "open-tasks")
        String key,
        @Schema(description = "Human-readable product label.", example = "Open tasks")
        String title,
        @Schema(description = "Current product readiness for this section.")
        WorkspaceCapabilityReadiness readiness,
        @Schema(description = "Support-safe summary without provider identifiers or raw errors.")
        String summary,
        @Schema(description = "Number of actionable items known to the Weave product layer.", example = "3")
        int itemCount,
        @Schema(description = "Whether the section has a keyboard and screen-reader path.", example = "true")
        boolean accessible,
        @Schema(description = "Backend-owned product route/surface, not a raw provider URL.", example = "weave://home/tasks")
        String productRoute) {
}
