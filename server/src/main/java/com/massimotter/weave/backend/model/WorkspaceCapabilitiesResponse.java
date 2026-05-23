package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Workspace capability snapshot exposed to Weave clients.")
public record WorkspaceCapabilitiesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse shellAccess,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse chat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse calendar,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse boards) {
}
