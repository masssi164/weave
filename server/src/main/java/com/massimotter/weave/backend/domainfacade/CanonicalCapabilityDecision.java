package com.massimotter.weave.backend.domainfacade;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Fail-closed canonical capability decision made before provider access.")
public record CanonicalCapabilityDecision(
        String domain,
        String capability,
        boolean allowed,
        CanonicalMemberState state,
        String reason,
        boolean providerAccessAllowed,
        boolean failClosed,
        boolean supportSafe,
        boolean secretMaterialReturned,
        boolean rawProviderErrorsReturned,
        Map<String, Object> supportSafeDiagnostics) {

    public CanonicalCapabilityDecision {
        supportSafeDiagnostics = supportSafeDiagnostics == null ? Map.of() : Map.copyOf(supportSafeDiagnostics);
    }
}
