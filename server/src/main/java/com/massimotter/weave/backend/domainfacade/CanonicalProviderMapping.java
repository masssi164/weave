package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Support-safe admin/operator provider mapping summary for a canonical Weave domain.")
public record CanonicalProviderMapping(
        String domain,
        String providerCategoryKey,
        String selectedProviderKey,
        String selectionSource,
        boolean selectedByAdmin,
        boolean configured,
        CanonicalMemberState readinessState,
        boolean failClosed,
        boolean supportSafe,
        boolean secretRefConfigured,
        boolean secretMaterialReturned,
        boolean downstreamPayloadsReturned,
        boolean rawProviderErrorsReturned,
        List<String> lossyMappingWarnings,
        Map<String, Object> supportSafeDiagnostics) {

    public CanonicalProviderMapping {
        lossyMappingWarnings = lossyMappingWarnings == null ? List.of() : List.copyOf(lossyMappingWarnings);
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
    }
}
