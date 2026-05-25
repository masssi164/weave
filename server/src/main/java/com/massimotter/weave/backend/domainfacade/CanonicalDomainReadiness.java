package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Schema(description = "Canonical domain readiness returned by Weave-owned facades before any provider adapter access.")
public record CanonicalDomainReadiness(
        String contractVersion,
        String domain,
        CanonicalMemberState memberState,
        String memberImpact,
        boolean failClosed,
        boolean supportSafe,
        boolean policyEvaluatedBeforeProviderAccess,
        boolean providerLookupPerformed,
        boolean memberClientMayConfigureProvider,
        boolean downstreamDiagnosticsExposedToMember,
        boolean rawProviderPayloadsReturned,
        boolean rawProviderErrorsReturned,
        boolean secretMaterialReturned,
        CanonicalDomainContract contract,
        List<CanonicalProviderMapping> providerMappings,
        Map<String, Object> supportSafeDiagnostics,
        Instant checkedAt) {

    public CanonicalDomainReadiness {
        providerMappings = providerMappings == null ? List.of() : List.copyOf(providerMappings);
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
    }
}
