package com.massimotter.weave.backend.model.calls;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Weave call leave acknowledgement.")
public record CallLeaveResponse(
        @Schema(description = "Weave call id.", example = "call_123")
        String callId,
        @Schema(description = "Whether the leave request was accepted.")
        boolean left,
        @Schema(description = "Support-safe audit/reference id.")
        String auditRef,
        @Schema(description = "Acknowledgement timestamp.")
        Instant leftAt) {
}
