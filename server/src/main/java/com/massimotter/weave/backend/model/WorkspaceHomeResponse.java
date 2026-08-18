package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Backend-owned Weave Home snapshot for the daily work loop.")
public record WorkspaceHomeResponse(
        @Schema(description = "Stable schema version for client compatibility.", example = "1")
        int version,
        @Schema(description = "Overall readiness of the daily work loop.")
        WorkspaceCapabilityReadiness readiness,
        @Schema(description = "Support-safe summary of the workspace day.")
        String summary,
        @Schema(description = "Daily work sections shown by Weave Home.")
        List<WorkspaceHomeSectionResponse> sections,
        @Schema(description = "Actionable follow-ups in priority order.")
        List<WorkspaceHomeActionResponse> actions,
        @Schema(description = "Newest completed support-safe activities the current member may view.")
        List<WorkspaceHomeRecentActivityResponse> recentActivity,
        @Schema(description = "True when the payload is intentionally free of raw provider URLs, IDs, filenames, usernames, and secrets.", example = "true")
        boolean supportSafe) {

    public WorkspaceHomeResponse {
        sections = sections == null ? List.of() : List.copyOf(sections);
        actions = actions == null ? List.of() : List.copyOf(actions);
        recentActivity = recentActivity == null ? List.of() : List.copyOf(recentActivity);
    }
}
