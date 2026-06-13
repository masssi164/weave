package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Readable channel Decision Ledger snapshot.")
public record DecisionLedgerRecordsResponse(
        String conversationId,
        String contextId,
        @Schema(description = "Whether background room reading was used to populate this response. Always false for Sprint 4.", example = "false")
        boolean backgroundRoomReadingEnabled,
        DecisionLedgerEvidencePostureResponse evidencePosture,
        List<DecisionLedgerRecordResponse> records) {
}
