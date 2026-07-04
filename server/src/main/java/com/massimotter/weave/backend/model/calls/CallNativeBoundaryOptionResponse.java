package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One native OS calls/meetings boundary option backed by Weave meeting grants.")
public record CallNativeBoundaryOptionResponse(
        @Schema(description = "Target platform family.", example = "ios")
        String platform,
        @Schema(description = "Native OS boundary to qualify.", example = "CallKitPushKit")
        String osBoundary,
        @Schema(description = "Flutter/native bridge role for setup, status, and incoming-call handoff only.",
                example = "pigeon-or-platform-channel")
        String bridge,
        @Schema(description = "Whether this native call boundary is ready for end-user setup.", example = "false")
        boolean available,
        @Schema(description = "Support-safe setup state.", example = "boundary_contract_ready")
        String setupState,
        @Schema(description = "Support-safe setup action or route. Does not expose raw media-provider URLs.",
                example = "open-weave-calls-native-setup")
        String setupAction,
        @Schema(description = "Native implementation contracts required before availability can be true.")
        List<String> requiredContracts,
        @Schema(description = "Support-safe notes for member/admin setup surfaces.")
        List<String> notes) {

    public CallNativeBoundaryOptionResponse {
        requiredContracts = requiredContracts == null ? List.of() : List.copyOf(requiredContracts);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }
}
