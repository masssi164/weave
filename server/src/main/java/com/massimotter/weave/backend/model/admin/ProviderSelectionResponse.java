package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Support-safe selected provider mapping owned by the Admin Console/backend control plane.")
public record ProviderSelectionResponse(
        String category,
        String providerKey,
        String choiceModel,
        String secretRef,
        String selectedBy,
        Instant selectedAt,
        boolean applied,
        boolean dryRun,
        boolean supportSafe,
        boolean bootstrapSuggestionOnly,
        boolean migrationDryRunRequired,
        List<String> lossyMappingNotes,
        String readiness,
        String persistencePosture,
        Instant evidenceFreshAt) {
    public ProviderSelectionResponse {
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
    }
}
