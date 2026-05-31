package com.massimotter.weave.backend.domainregistry;

import java.util.List;
import java.util.Map;

public record CanonicalDomainRegistryEntryResponse(
        String key,
        int version,
        String displayName,
        String purpose,
        List<String> canonicalObjects,
        List<String> capabilityKeys,
        List<String> memberStates,
        List<String> adminStates,
        List<String> sourceOfTruthModes,
        List<String> portabilityRequirements,
        List<String> adapterManifestRequirements,
        List<String> compatibilityAliases,
        Map<String, String> providerRealityLevelByCandidate) {

    public CanonicalDomainRegistryEntryResponse {
        canonicalObjects = canonicalObjects == null ? List.of() : List.copyOf(canonicalObjects);
        capabilityKeys = capabilityKeys == null ? List.of() : List.copyOf(capabilityKeys);
        memberStates = memberStates == null ? List.of() : List.copyOf(memberStates);
        adminStates = adminStates == null ? List.of() : List.copyOf(adminStates);
        sourceOfTruthModes = sourceOfTruthModes == null ? List.of() : List.copyOf(sourceOfTruthModes);
        portabilityRequirements = portabilityRequirements == null ? List.of() : List.copyOf(portabilityRequirements);
        adapterManifestRequirements = adapterManifestRequirements == null ? List.of() : List.copyOf(adapterManifestRequirements);
        compatibilityAliases = compatibilityAliases == null ? List.of() : List.copyOf(compatibilityAliases);
        providerRealityLevelByCandidate = providerRealityLevelByCandidate == null ? Map.of() : Map.copyOf(providerRealityLevelByCandidate);
    }
}
