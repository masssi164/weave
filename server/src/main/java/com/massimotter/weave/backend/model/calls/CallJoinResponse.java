package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Short-lived Weave call join grant.")
public record CallJoinResponse(
        @Schema(description = "Weave call id.", example = "call_123")
        String callId,
        @Schema(description = "Weave room reference used by the media provider.", example = "weave-call-call_123")
        String roomRef,
        @Schema(description = "Selected media provider key.", example = "livekit")
        String mediaProvider,
        @Schema(description = "Media join URL for the selected provider.")
        String joinUrl,
        @Schema(description = "Short-lived access token scoped to this call and participant.")
        String accessToken,
        @Schema(description = "Grant expiration timestamp.")
        Instant expiresAt) {
}
