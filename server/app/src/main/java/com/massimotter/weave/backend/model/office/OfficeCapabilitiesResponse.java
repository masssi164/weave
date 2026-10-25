package com.massimotter.weave.backend.model.office;

import com.massimotter.weave.backend.provider.ProviderStatusResponse;
import java.util.List;

public record OfficeCapabilitiesResponse(
        String releaseStatus,
        boolean enabled,
        boolean configured,
        boolean supportSafe,
        String launchMode,
        String defaultProvider,
        List<ProviderStatusResponse> providerReadiness,
        List<OfficeProviderCandidateResponse> candidates,
        OfficeCapabilityFlagsResponse capabilities,
        List<String> supportedFileTypes,
        OfficePermissionModelResponse permissions,
        OfficeLockSessionReadinessResponse lockSessionReadiness) {

    public OfficeCapabilitiesResponse {
        providerReadiness = providerReadiness == null ? List.of() : List.copyOf(providerReadiness);
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        supportedFileTypes = supportedFileTypes == null ? List.of() : List.copyOf(supportedFileTypes);
    }
}
