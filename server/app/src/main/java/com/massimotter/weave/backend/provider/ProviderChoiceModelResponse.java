package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Admin-visible provider choice posture for a category. It records privacy/compliance risk notes without changing member-facing product semantics.")
public record ProviderChoiceModelResponse(
        @Schema(description = "Stable choice model key: recommended_self_hosted_default, external_existing_provider, or managed_cloud_provider.") String choiceModel,
        @Schema(description = "Support-safe adapter keys covered by this choice model.") List<String> adapters,
        @Schema(description = "Support-safe admin/operator risk notes. No secrets, URLs, tenant IDs, or raw provider errors.") List<String> adminRiskNotes,
        @Schema(description = "Whether this is the recommended sovereign/default posture.") boolean recommended) {

    public ProviderChoiceModelResponse {
        choiceModel = requireText(choiceModel, "choiceModel");
        adapters = adapters == null ? List.of() : List.copyOf(adapters);
        adminRiskNotes = adminRiskNotes == null ? List.of() : List.copyOf(adminRiskNotes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
