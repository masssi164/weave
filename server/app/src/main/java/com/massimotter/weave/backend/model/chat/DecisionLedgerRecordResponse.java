package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "First-class channel Decision Ledger record.")
public record DecisionLedgerRecordResponse(
        @Schema(description = "Stable Weave decision id.", example = "decision-123")
        String id,
        @Schema(description = "Stable Weave conversation/channel id.", example = "channel-general")
        String conversationId,
        @Schema(description = "Weave Context/Space id that owns the decision.", example = "workspace-default")
        String contextId,
        String title,
        @Schema(description = "Lifecycle state.", allowableValues = {"proposed", "accepted", "superseded", "rejected"})
        String status,
        @Schema(description = "Weave principal reference, not a raw provider user id.", example = "user:alice")
        String authorRef,
        Instant decidedAt,
        List<DecisionLedgerReferenceResponse> references,
        List<String> risks,
        List<String> openQuestions,
        List<String> followUpRefs,
        @Schema(description = "Whether this record is safe for member responses and support bundles.", example = "true")
        boolean supportSafe) {
}
