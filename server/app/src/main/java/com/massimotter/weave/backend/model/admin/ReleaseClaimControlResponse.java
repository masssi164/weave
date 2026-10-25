package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe RC release-claim control surface for administrators.")
public record ReleaseClaimControlResponse(
        String claimState,
        String candidateTag,
        String pinnedSpecCorpusRef,
        String releaseNotesSource,
        String supportBundleRef,
        String accessibilityEvidenceRef,
        List<String> unresolvedVetoes,
        List<RcEvidenceGateReadinessResponse> gates) {
    public ReleaseClaimControlResponse {
        unresolvedVetoes = unresolvedVetoes == null ? List.of() : List.copyOf(unresolvedVetoes);
        gates = gates == null ? List.of() : List.copyOf(gates);
    }
}
