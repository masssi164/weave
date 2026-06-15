package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Provider-neutral Admin domain binding state. Plans/impact reports are transition artifacts; this binding is the primary admin object.")
public record DomainBindingResponse(
        String domainKey,
        String label,
        ProviderCategoryReadiness posture,
        String activeBinding,
        ProviderConnectionRefResponse providerConnectionRef,
        List<DomainAdapterCandidateResponse> candidateBindings,
        List<ProviderAdapterReadinessEvidenceResponse> capabilityStates,
        List<String> transitionArtifacts,
        boolean exactlyOneActiveBinding,
        boolean supportSafe) {

    public DomainBindingResponse {
        domainKey = requireText(domainKey, "domainKey");
        label = requireText(label, "label");
        posture = posture == null ? ProviderCategoryReadiness.DISABLED : posture;
        candidateBindings = candidateBindings == null ? List.of() : List.copyOf(candidateBindings);
        capabilityStates = capabilityStates == null ? List.of() : List.copyOf(capabilityStates);
        transitionArtifacts = transitionArtifacts == null ? List.of() : List.copyOf(transitionArtifacts);
        exactlyOneActiveBinding = candidateBindings.stream().filter(DomainAdapterCandidateResponse::active).count() == 1;
        supportSafe = supportSafe && (providerConnectionRef == null || providerConnectionRef.supportSafe());
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
