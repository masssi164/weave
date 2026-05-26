package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Support-safe source reference for a channel decision record.")
public record DecisionLedgerReferenceRequest(
        @NotBlank
        @Pattern(regexp = "chat-message|file|meeting|task")
        @Schema(description = "Reference type in Weave vocabulary.", example = "chat-message")
        String type,
        @NotBlank
        @Size(max = 160)
        @Schema(description = "Stable Weave reference, never a raw provider id or URL.", example = "message:msg-seed-welcome")
        String ref,
        @NotBlank
        @Size(max = 120)
        @Schema(description = "Member-visible support-safe source label.", example = "Message from Alex")
        String label,
        @Size(max = 280)
        @Schema(description = "Optional short excerpt or evidence label.")
        String excerpt) {
}
