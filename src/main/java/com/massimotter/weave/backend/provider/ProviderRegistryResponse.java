package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;

public record ProviderRegistryResponse(
        String releaseStatus,
        boolean backendOwnedFacades,
        boolean flutterDirectProviderCallsAllowed,
        boolean supportSafe,
        Instant generatedAt,
        List<ProviderStatusResponse> providers) {

    public ProviderRegistryResponse {
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
