package com.massimotter.weave.backend.provider;

import java.time.Instant;
import java.util.List;

public record ProviderRegistryResponse(
        String releaseStatus,
        String providerConfigSource,
        boolean bootstrapDefaultsAreSuggestionsOnly,
        boolean adminSelectedMappingsRequired,
        boolean backendOwnedFacades,
        boolean flutterDirectProviderCallsAllowed,
        boolean supportSafe,
        Instant generatedAt,
        List<ProviderSelection> selectedProviderMappings,
        List<ProviderCategoryStatusResponse> categories,
        List<ProviderStatusResponse> providers) {

    public ProviderRegistryResponse {
        selectedProviderMappings = selectedProviderMappings == null ? List.of() : List.copyOf(selectedProviderMappings);
        categories = categories == null ? List.of() : List.copyOf(categories);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
