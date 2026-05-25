package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Support-safe adapter candidate in the domain adapter registry. No endpoints, credentials, or raw provider diagnostics are exposed.")
public record DomainAdapterCandidateResponse(
        String adapterKey,
        String choiceModel,
        boolean active,
        boolean configured,
        ProviderCategoryReadiness readiness,
        List<String> migrationSupport,
        List<String> riskNotes,
        boolean supportSafe,
        Map<String, Object> diagnostics) {

    public DomainAdapterCandidateResponse {
        adapterKey = requireText(adapterKey, "adapterKey");
        choiceModel = requireText(choiceModel, "choiceModel");
        readiness = readiness == null ? ProviderCategoryReadiness.DISABLED : readiness;
        migrationSupport = migrationSupport == null ? List.of() : List.copyOf(migrationSupport);
        riskNotes = riskNotes == null ? List.of() : List.copyOf(riskNotes);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
