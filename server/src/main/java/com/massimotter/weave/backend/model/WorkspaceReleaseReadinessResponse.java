package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Operator-facing workspace readiness snapshot for the workspace.")
public record WorkspaceReleaseReadinessResponse(
        @Schema(description = "Overall workspace readiness for the currently configured workspace.")
        WorkspaceCapabilityReadiness readiness,
        @Schema(description = "Short summary of the current release posture.")
        String summary,
        @Schema(description = "Readiness checks for the core product slice.")
        List<WorkspaceReleaseReadinessCheckResponse> checks,
        @Schema(description = "Outstanding operator actions extracted from the failing checks.")
        List<String> actions) {
}
