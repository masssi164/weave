package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Read-only Weaver scout request over explicit allowed channel context.")
public record WeaverScoutSummaryRequest(
        @NotBlank
        @Size(max = 240)
        String question,
        @Size(max = 160)
        @Schema(description = "Optional future write/action the member is asking about. It will be blocked/proposed, not executed.")
        String requestedAction) {
}
