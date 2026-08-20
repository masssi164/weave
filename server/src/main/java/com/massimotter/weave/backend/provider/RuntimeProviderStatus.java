package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds registry evidence from the adapter that is actually bound at runtime.
 *
 * <p>This deliberately does not call a remote provider readiness endpoint. Reachability
 * belongs to the cached provider-capability health service. Files may attach a fresh,
 * bounded capability observation from Weave-owned local authority state, but registry
 * rendering never creates downstream provider traffic.</p>
 */
public final class RuntimeProviderStatus {

    private RuntimeProviderStatus() {
    }

    public static ProviderPort fromConformancePort(
            ProviderModule module,
            String providerKey,
            boolean configured,
            ProviderConformanceProfile conformance,
            String summary,
            List<String> candidates) {
        return fromConformancePort(
                module,
                providerKey,
                configured,
                conformance,
                summary,
                candidates,
                Map.of());
    }

    public static ProviderPort fromConformancePort(
            ProviderModule module,
            String providerKey,
            boolean configured,
            ProviderConformanceProfile conformance,
            String summary,
            List<String> candidates,
            Map<String, Object> additionalDiagnostics) {
        ProviderRealityLevel reality = !configured
                ? ProviderRealityLevel.CONTRACT_ONLY
                : ProviderRealityLevel.CONFIGURED;
        Map<String, Object> diagnostics = new LinkedHashMap<>();
        diagnostics.put("canonicalDomain", conformance.domain());
        diagnostics.put("adapterKey", conformance.adapterKey());
        diagnostics.put("atomicWrites", conformance.atomicWrites());
        diagnostics.put("stableVersionTokens", conformance.stableVersionTokens());
        diagnostics.put("runtimeBindingObserved", true);
        diagnostics.put("reachabilitySource", "cached-provider-capability-health");
        if (additionalDiagnostics != null) {
            diagnostics.putAll(additionalDiagnostics);
        }
        return fixed(
                module,
                providerKey,
                configured,
                summary,
                conformance.supportedOperations(),
                Set.of(),
                candidates,
                reality,
                diagnostics);
    }

    public static ProviderPort fixed(
            ProviderModule module,
            String providerKey,
            boolean configured,
            String summary,
            Set<String> supportedCapabilities,
            Set<String> unsupportedOperations,
            List<String> candidates,
            ProviderRealityLevel reality,
            Map<String, Object> diagnostics) {
        Map<String, Object> safeDiagnostics = new LinkedHashMap<>(diagnostics == null ? Map.of() : diagnostics);
        safeDiagnostics.put("secretsReturned", false);
        safeDiagnostics.put("rawProviderErrorsReturned", false);
        safeDiagnostics.put("runtimeBindingObserved", true);
        return new StaticProviderPort(new ProviderStatusResponse(
                module,
                providerKey,
                configured ? ProviderState.CONFIGURED : ProviderState.NOT_CONFIGURED,
                configured ? "configured_pending_cached_health" : "not_configured",
                true,
                configured,
                false,
                true,
                true,
                false,
                summary,
                supportedCapabilities,
                unsupportedOperations,
                List.of("provider-not-configured", "provider-disabled", "provider-unavailable", "unsupported-capability"),
                "support-safe runtime binding evidence; reachability comes only from the cached health service",
                candidates,
                reality,
                safeDiagnostics));
    }

}
