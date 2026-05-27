package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Schema(description = "Support-safe provider replacement preflight. This validates an adapter swap without applying provider state.")
public record ProviderReplacementDryRunRequest(
        @NotBlank String category,
        @NotBlank String currentAdapter,
        @NotBlank String targetAdapter,
        String choiceModel,
        @NotBlank String secretRef,
        @NotBlank String sourceOfTruth,
        List<String> lossyMappingNotes,
        String reason) {
    public ProviderReplacementDryRunRequest {
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
    }
}
