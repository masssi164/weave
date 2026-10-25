package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

@Schema(description = "Support-safe provider replacement preflight. This validates an adapter swap without applying provider state.")
public record ProviderReplacementDryRunRequest(
        @NotBlank String category,
        @NotBlank String currentAdapter,
        @NotBlank String targetAdapter,
        String choiceModel,
        @NotBlank String secretRef,
        @NotBlank String sourceOfTruth,
        List<String> lossyMappingNotes,
        boolean portableExportImportRequired,
        Map<String, String> requestedSwitchPlan,
        String reason) {
    public ProviderReplacementDryRunRequest {
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
        requestedSwitchPlan = requestedSwitchPlan == null ? Map.of() : Map.copyOf(requestedSwitchPlan);
    }
}
