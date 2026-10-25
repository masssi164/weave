package com.massimotter.weave.backend.capability.api;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Workspace capability snapshot exposed to Weave clients.")
public record WorkspaceCapabilitiesResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse shellAccess,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse chat,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse files,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse calendar,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse boards,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse meetingsCalls,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse documentsCollaboration,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse decisionsEvidence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse manualsHelp,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse releaseEvidence,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse adminControlPlane,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) WorkspaceCapabilityStatusResponse agentRuntimeControl) {

}
