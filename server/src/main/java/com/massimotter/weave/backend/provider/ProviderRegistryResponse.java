package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;

public record ProviderRegistryResponse(
        String releaseStatus,
        boolean backendOwnedFacades,
        boolean flutterDirectProviderCallsAllowed,
        boolean supportSafe,
        Instant generatedAt,
        DomainAdapterRegistryResponse domainAdapterRegistry,
        List<ProviderCategoryStatusResponse> categories,
        List<ProviderStatusResponse> providers) {

    public ProviderRegistryResponse {
        domainAdapterRegistry = domainAdapterRegistry == null
                ? new DomainAdapterRegistryResponse(null, false, false, false, generatedAt, List.of())
                : domainAdapterRegistry;
        categories = categories == null ? List.of() : List.copyOf(categories);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
