package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe RC evidence gate shown before go-live/release claims.")
public record RcEvidenceGateReadinessResponse(
        String key,
        String label,
        String state,
        String evidenceFreshness,
        List<String> evidenceRefs,
        String nextAction,
        boolean blocksReleaseClaim) {
    public RcEvidenceGateReadinessResponse {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
