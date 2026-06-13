package com.massimotter.weave.backend.model.chat;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe Decisions/Evidence posture for provenance, audit metadata, and export boundary.")
public record DecisionLedgerEvidencePostureResponse(
        @Schema(description = "Human-readable provenance summary without raw provider identifiers.")
        String provenance,
        @Schema(description = "Support-safe audit references for this decision snapshot.")
        List<String> auditRefs,
        @Schema(description = "Export posture summary for decisions/evidence records.")
        String exportPosture,
        @Schema(description = "Whether the posture remains safe for member/support surfaces.", example = "true")
        boolean supportSafe) {

    public DecisionLedgerEvidencePostureResponse {
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }
}
