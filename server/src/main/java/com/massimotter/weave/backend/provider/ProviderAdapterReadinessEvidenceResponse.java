package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Schema(description = "Support-safe Admin Console evidence for one domain adapter candidate.")
public record ProviderAdapterReadinessEvidenceResponse(
        @Schema(description = "Provider-neutral Weave domain/category key.") String domain,
        @Schema(description = "Support-safe adapter key; never an endpoint URL or credential.") String adapterKey,
        @Schema(description = "True when backend/operator configuration is present for the selected adapter.") boolean configured,
        @Schema(description = "True when the backend can infer a reachable adapter without exposing raw diagnostics.") boolean reachable,
        @Schema(description = "Support-safe readiness/health state for this adapter.") String health,
        @Schema(description = "True when unavailable provider access remains fail-closed behind Weave facades.") boolean failClosed,
        @Schema(description = "Booleans/counts/stable codes only; no endpoints, secrets, tokens, or raw upstream errors.") Map<String, Object> supportSafeDiagnostics,
        @Schema(description = "UTC timestamp for the evidence snapshot.") Instant evidenceTimestamp) {

    public ProviderAdapterReadinessEvidenceResponse {
        domain = requireText(domain, "domain");
        adapterKey = requireText(adapterKey, "adapterKey");
        health = requireText(health, "health");
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(supportSafeDiagnostics));
        evidenceTimestamp = evidenceTimestamp == null ? Instant.EPOCH : evidenceTimestamp;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
