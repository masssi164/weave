package com.massimotter.weave.backend.model;

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

    public WorkspaceCapabilitiesResponse(
            WorkspaceCapabilityStatusResponse shellAccess,
            WorkspaceCapabilityStatusResponse chat,
            WorkspaceCapabilityStatusResponse files,
            WorkspaceCapabilityStatusResponse calendar,
            WorkspaceCapabilityStatusResponse boards,
            WorkspaceCapabilityStatusResponse agentRuntimeControl) {
        this(
                shellAccess,
                chat,
                files,
                calendar,
                boards,
                disabled("meetings/calls"),
                disabled("documents/collaboration"),
                ready("decisions/evidence"),
                ready("manuals/help"),
                ready("release evidence"),
                ready("admin control plane"),
                agentRuntimeControl);
    }

    private static WorkspaceCapabilityStatusResponse disabled(String category) {
        return new WorkspaceCapabilityStatusResponse(
                false,
                WorkspaceCapabilityReadiness.UNAVAILABLE,
                WorkspaceCapabilityPolicyState.DISABLED,
                null,
                category + " is disabled or not member-facing in this workspace.",
                java.util.List.of());
    }

    private static WorkspaceCapabilityStatusResponse ready(String category) {
        return new WorkspaceCapabilityStatusResponse(
                true,
                WorkspaceCapabilityReadiness.READY,
                WorkspaceCapabilityPolicyState.ALLOWED,
                "system-internal-readiness",
                category + " is represented by backend-owned Weave domain evidence.",
                java.util.List.of());
    }
}
