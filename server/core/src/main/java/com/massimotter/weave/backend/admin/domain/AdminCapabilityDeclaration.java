package com.massimotter.weave.backend.admin.domain;

import java.util.List;

/** One installed domain's existing Admin simulation and profile capabilities. */
public record AdminCapabilityDeclaration(
        String owner, int order, List<String> knownCapabilities,
        List<String> administratorGrants, List<String> operatorGrants, List<String> memberGrants,
        List<ProfileGrant> profiles) {
    public AdminCapabilityDeclaration {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Admin capability owner is required");
        knownCapabilities = List.copyOf(knownCapabilities);
        administratorGrants = List.copyOf(administratorGrants);
        operatorGrants = List.copyOf(operatorGrants);
        memberGrants = List.copyOf(memberGrants);
        profiles = List.copyOf(profiles);
    }

    public record ProfileGrant(String profile, int order, List<String> capabilities) {
        public ProfileGrant {
            capabilities = List.copyOf(capabilities);
        }
    }
}
