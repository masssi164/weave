package com.massimotter.weave.backend.model.interop;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Disabled-by-default interop gateway status and support-safe contract.")
public record InteropStatusResponse(
        boolean enabled,
        String readiness,
        List<ExternalConnectionResponse> connections,
        ConsentCapabilityRegistryResponse consentRegistry,
        List<String> auditEventTypes,
        List<String> rateLimitStates,
        List<String> degradedStates,
        SupportBundlePolicyResponse supportBundle,
        ConnectorBoundarySummaryResponse connectorBoundary) {

    public record ExternalConnectionResponse(
            String provider,
            String status,
            String readiness,
            String credentialState,
            String mappingState,
            List<String> capabilities,
            List<String> actions) {
    }

    public record ConsentCapabilityRegistryResponse(List<CapabilityResponse> capabilities) {
    }

    public record CapabilityResponse(String key, String description, boolean requiresAdminConsent) {
    }

    public record SupportBundlePolicyResponse(
            String redactionMode,
            boolean providerSecretsRedacted,
            List<String> redactedFields) {
    }

    public record ConnectorBoundarySummaryResponse(
            boolean publicSdkAvailable,
            String runtimeBoundary,
            List<String> rules) {
    }
}
