package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StaticProviderPort implements ProviderPort {

    private final ProviderStatusResponse status;

    public StaticProviderPort(ProviderStatusResponse status) {
        this.status = status;
    }

    public static StaticProviderPort pending(
            ProviderModule module,
            String providerKey,
            String summary,
            Set<String> supportedCapabilities,
            Set<String> unsupportedOperations,
            List<String> candidates,
            Map<String, Object> diagnostics) {
        return new StaticProviderPort(new ProviderStatusResponse(
                module,
                providerKey,
                ProviderState.NOT_CONFIGURED,
                "not_configured",
                false,
                false,
                true,
                true,
                true,
                false,
                summary,
                supportedCapabilities,
                unsupportedOperations,
                List.of("provider-not-configured", "provider-disabled", "provider-unavailable", "unsupported-capability"),
                "support-safe registry seam; secrets and raw upstream errors are redacted before API output",
                candidates,
                diagnostics));
    }

    @Override
    public ProviderStatusResponse status() {
        return status;
    }
}
