package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds registry evidence from the adapter that is actually bound at runtime.
 *
 * <p>This deliberately does not call a provider readiness endpoint. Reachability
 * belongs to the cached provider-capability health service; rendering registry
 * status must never create downstream load.</p>
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
        ProviderRealityLevel reality = !configured
                ? ProviderRealityLevel.CONTRACT_ONLY
                : ProviderRealityLevel.CONFIGURED;
        return fixed(
                module,
                providerKey,
                configured,
                summary,
                conformance.supportedOperations(),
                Set.of(),
                candidates,
                reality,
                Map.of(
                        "canonicalDomain", conformance.domain(),
                        "adapterKey", conformance.adapterKey(),
                        "atomicWrites", conformance.atomicWrites(),
                        "stableVersionTokens", conformance.stableVersionTokens(),
                        "runtimeBindingObserved", true,
                        "reachabilitySource", "cached-provider-capability-health"));
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
