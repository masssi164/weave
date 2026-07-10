package com.massimotter.weave.backend.model.calls;

import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Executable native Calls/Meetings boundary contract for OS call surfaces.")
public record CallNativeBoundarySetupResponse(
        @Schema(description = "Meetings/calls capability readiness as seen by the authenticated member.")
        WorkspaceCapabilityStatusResponse readiness,
        @Schema(description = "True when this setup contract excludes raw media-provider endpoints, credentials, tokens, and diagnostics.",
                example = "true")
        boolean supportSafe,
        @Schema(description = "False: member/native setup must not receive raw media-provider configuration.",
                example = "false")
        boolean providerConfigurationExposed,
        @Schema(description = "False: this contract never returns provider credentials, API keys, or bearer tokens.",
                example = "false")
        boolean credentialsExposed,
        @Schema(description = "Weave-owned meetings facade base path for native setup/status.", example = "/api/calls")
        String facadeBasePath,
        @Schema(description = "Weave-owned join-grant path template for the current meeting transport.",
                example = "/api/calls/meetings/{meetingId}/join-grants")
        String joinGrantPathTemplate,
        @Schema(description = "Provider-neutral signaling requirement for native call invitations.")
        String signalingBoundary,
        @Schema(description = "Provider-neutral media/control-plane split for native call UI.")
        String mediaBoundary,
        @Schema(description = "OS-specific native call setup options.")
        List<CallNativeBoundaryOptionResponse> options,
        @Schema(description = "Executable proof hooks that can be exercised before full native call availability.")
        List<String> proofHooks,
        @Schema(description = "Support-safe blockers before native call availability can be true.")
        List<String> blockedUntil) {

    public CallNativeBoundarySetupResponse {
        options = options == null ? List.of() : List.copyOf(options);
        proofHooks = proofHooks == null ? List.of() : List.copyOf(proofHooks);
        blockedUntil = blockedUntil == null ? List.of() : List.copyOf(blockedUntil);
    }
}
