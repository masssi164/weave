package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.Map;

@Schema(description = "Admin-owned deny-by-default category capability whitelist snapshot.")
public record CapabilityWhitelistResponse(
        boolean denyByDefault,
        boolean normalMembersMayAuthorPolicy,
        List<String> stableMemberImpactStates,
        Map<String, List<String>> profileCapabilities,
        List<String> selectedProfileKeys,
        List<String> effectiveCapabilities,
        String sourceOfTruth) {
    public CapabilityWhitelistResponse {
        stableMemberImpactStates = stableMemberImpactStates == null ? List.of() : List.copyOf(stableMemberImpactStates);
        profileCapabilities = profileCapabilities == null ? Map.of() : Map.copyOf(profileCapabilities);
        selectedProfileKeys = selectedProfileKeys == null ? List.of() : List.copyOf(selectedProfileKeys);
        effectiveCapabilities = effectiveCapabilities == null ? List.of() : List.copyOf(effectiveCapabilities);
    }
}
