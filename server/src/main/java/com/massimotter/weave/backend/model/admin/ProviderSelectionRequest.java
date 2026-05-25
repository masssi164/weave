package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Admin-selected provider mapping. This is control-plane state; bootstrap defaults are only suggestions until this request is applied.")
public record ProviderSelectionRequest(
        @NotBlank String category,
        @NotBlank String providerKey,
        String choiceModel,
        String secretRef,
        boolean dryRun,
        List<String> lossyMappingNotes,
        String reason) {
    public ProviderSelectionRequest {
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
    }
}
