package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;

public record ProviderRegistryResponse(
        String releaseStatus,
        boolean backendOwnedFacades,
        boolean flutterDirectProviderCallsAllowed,
        boolean supportSafe,
        Instant generatedAt,
        List<ProviderCategoryStatusResponse> categories,
        List<ProviderStatusResponse> providers) {

    public ProviderRegistryResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
