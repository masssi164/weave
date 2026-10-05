package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistry;
import com.massimotter.weave.backend.domainregistry.CanonicalDomainRegistryResponse;
import com.massimotter.weave.backend.model.admin.FilesBindingStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
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
        CanonicalDomainRegistryResponse canonicalDomainRegistry,
        DomainAdapterRegistryResponse domainAdapterRegistry,
        @Schema(description = "Legacy deployment configuration selections; not evidence of active organization runtime bindings.")
        List<ProviderSelection> selectedProviderMappings,
        List<ProviderCategoryStatusResponse> categories,
        List<ProviderStatusResponse> providers,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Configured canonical deployment organization.")
        String organizationId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED)
        FilesBindingStatusResponse filesBinding) {

    public ProviderRegistryResponse {
        canonicalDomainRegistry = canonicalDomainRegistry == null
                ? CanonicalDomainRegistry.snapshot()
                : canonicalDomainRegistry;
        domainAdapterRegistry = domainAdapterRegistry == null
                ? new DomainAdapterRegistryResponse(null, false, false, false, generatedAt, List.of())
                : domainAdapterRegistry;
        selectedProviderMappings = selectedProviderMappings == null ? List.of() : List.copyOf(selectedProviderMappings);
        categories = categories == null ? List.of() : List.copyOf(categories);
        providers = providers == null ? List.of() : List.copyOf(providers);
    }
}
