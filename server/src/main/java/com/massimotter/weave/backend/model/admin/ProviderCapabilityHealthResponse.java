package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;

@Schema(description = "Role-gated, support-safe cached provider capability observations.")
public record ProviderCapabilityHealthResponse(
        String schemaVersion,
        Instant generatedAt,
        boolean supportSafe,
        List<CapabilityHealth> capabilities) {

    public ProviderCapabilityHealthResponse {
        schemaVersion = schemaVersion == null ? "provider-capability-health-v1" : schemaVersion;
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }

    public record CapabilityHealth(
            String capability,
            @Schema(allowableValues = {"available", "degraded", "unavailable"})
            String state,
            String supportSafeCode,
            String correlationRef,
            Instant observedAt,
            Instant nextProbeAt,
            Instant backoffUntil,
            Long cachedAgeSeconds,
            boolean stale,
            int consecutiveFailures,
            long probeLatencyMillis,
            long readinessTransitions) {
    }
}
