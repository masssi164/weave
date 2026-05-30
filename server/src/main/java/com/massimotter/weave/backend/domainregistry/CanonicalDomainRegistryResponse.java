package com.massimotter.weave.backend.domainregistry;

import java.util.List;
import java.util.Map;

public record CanonicalDomainRegistryResponse(
        String registryVersion,
        List<String> memberStates,
        List<String> adminStates,
        List<String> lossClasses,
        List<CanonicalDomainRegistryEntryResponse> domains,
        Map<String, String> compatibilityAliases,
        boolean supportSafe,
        boolean providerNamesInMemberContractsAllowed) {

    public CanonicalDomainRegistryResponse {
        memberStates = memberStates == null ? List.of() : List.copyOf(memberStates);
        adminStates = adminStates == null ? List.of() : List.copyOf(adminStates);
        lossClasses = lossClasses == null ? List.of() : List.copyOf(lossClasses);
        domains = domains == null ? List.of() : List.copyOf(domains);
        compatibilityAliases = compatibilityAliases == null ? Map.of() : Map.copyOf(compatibilityAliases);
    }
}
