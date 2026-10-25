package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Support-safe source reference attached to a channel decision record.")
public record DecisionLedgerReferenceResponse(
        @Schema(description = "Reference type in Weave vocabulary.", example = "chat-message")
        String type,
        @Schema(description = "Stable Weave reference, never a raw provider id or URL.", example = "message:msg-seed-welcome")
        String ref,
        @Schema(description = "Member-visible support-safe source label.", example = "Message from Alex")
        String label,
        @Schema(description = "Short support-safe evidence excerpt.")
        String excerpt) {
}
