package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Weave Calls control-plane state.")
public record CallResponse(
        @Schema(description = "Weave call id.", example = "call_123")
        String callId,
        @Schema(description = "Weave room reference used for join grants.", example = "weave-call-call_123")
        String roomRef,
        @Schema(description = "Selected media provider key.", example = "livekit")
        String mediaProvider,
        @Schema(description = "Whether join grants can currently be issued.")
        boolean joinAvailable,
        @Schema(description = "Whether the call has been ended.")
        boolean ended,
        @Schema(description = "Call title.")
        String title,
        @Schema(description = "Context/Space reference.")
        String spaceId,
        @Schema(description = "Support-safe linked calendar refs.")
        List<String> linkedCalendarRefs,
        @Schema(description = "Support-safe linked chat refs.")
        List<String> linkedChatRefs,
        @Schema(description = "Support-safe linked file refs.")
        List<String> linkedFileRefs,
        @Schema(description = "Support-safe linked decision refs.")
        List<String> linkedDecisionRefs,
        @Schema(description = "Last state update timestamp.")
        Instant updatedAt) {
}
