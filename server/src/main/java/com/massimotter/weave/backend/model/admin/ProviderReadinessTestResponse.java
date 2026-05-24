package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Support-safe provider readiness test result.")
public record ProviderReadinessTestResponse(
        String providerKey,
        String state,
        String readiness,
        boolean auditEventPublished,
        boolean supportSafe,
        boolean rawSecretExposed,
        Map<String, Object> diagnostics) {
    public ProviderReadinessTestResponse {
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(diagnostics);
    }
}
