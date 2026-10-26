package com.massimotter.weave.backend.admin.api;

import com.massimotter.weave.backend.discovery.api.ProviderCategoryStatusResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Organization/Admin Console control-plane snapshot served only by backend APIs.")
public record AdminControlPlaneResponse(
        String contractVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) AdminCurrentActorResponse currentActor,
        String organizationId,
        String displayName,
        String recommendedIdentityBroker,
        String providerConfigSource,
        boolean bootstrapDefaultsAreSuggestionsOnly,
        boolean backendOwnedFacades,
        boolean denyByDefaultPolicy,
        boolean supportSafe,
        boolean memberClientMayConfigureProviders,
        Instant generatedAt,
        List<ProviderCategoryStatusResponse> categories,
        List<ProviderSelectionResponse> selectedProviderMappings,
        CapabilityWhitelistResponse whitelist,
        PlatformIdentityReadinessResponse platformIdentityReadiness,
        List<SuiteDomainReadinessResponse> suiteDomainReadiness,
        GoLiveReadinessResponse goLiveReadiness,
        List<SecretRefResponse> secretRefs,
        List<McpServerBindingResponse> mcpServerBindings,
        Map<String, String> adminApiRoutes) {
    public AdminControlPlaneResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
        selectedProviderMappings = selectedProviderMappings == null ? List.of() : List.copyOf(selectedProviderMappings);
        suiteDomainReadiness = suiteDomainReadiness == null ? List.of() : List.copyOf(suiteDomainReadiness);
        secretRefs = secretRefs == null ? List.of() : List.copyOf(secretRefs);
        mcpServerBindings = mcpServerBindings == null ? List.of() : List.copyOf(mcpServerBindings);
        adminApiRoutes = adminApiRoutes == null ? Map.of() : Map.copyOf(adminApiRoutes);
    }
}
