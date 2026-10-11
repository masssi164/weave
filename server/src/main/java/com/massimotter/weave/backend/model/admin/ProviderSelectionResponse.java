package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Support-safe category-selection metadata; this response is not active organization binding or cutover evidence.")
public record ProviderSelectionResponse(
        String category,
        String providerKey,
        String choiceModel,
        String secretRef,
        String selectedBy,
        Instant selectedAt,
        @Schema(description = "Category metadata was recorded; this does not mean the active organization binding changed.")
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
