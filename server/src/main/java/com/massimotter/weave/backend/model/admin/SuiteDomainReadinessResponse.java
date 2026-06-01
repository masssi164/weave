package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Support-safe suite domain readiness row for Admin Console go-live decisions.")
public record SuiteDomainReadinessResponse(
        String domain,
        String label,
        String adminReadiness,
        String memberState,
        String selectedAdapterPosture,
        String sourceOfTruthMode,
        List<String> providerCategoryKeys,
        List<String> canonicalObjectKinds,
        List<String> capabilityStates,
        List<String> supportSafeErrors,
        List<String> portabilityNotes,
        List<String> auditRefs,
        String nextAction,
        boolean backendOwnedFacade,
        boolean providerMappingOwnedByServer,
        boolean rawProviderConfigExposedToMembers,
        Map<String, Object> evidence) {
    public SuiteDomainReadinessResponse {
        providerCategoryKeys = providerCategoryKeys == null ? List.of() : List.copyOf(providerCategoryKeys);
        canonicalObjectKinds = canonicalObjectKinds == null ? List.of() : List.copyOf(canonicalObjectKinds);
        capabilityStates = capabilityStates == null ? List.of() : List.copyOf(capabilityStates);
        supportSafeErrors = supportSafeErrors == null ? List.of() : List.copyOf(supportSafeErrors);
        portabilityNotes = portabilityNotes == null ? List.of() : List.copyOf(portabilityNotes);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
    }
}
