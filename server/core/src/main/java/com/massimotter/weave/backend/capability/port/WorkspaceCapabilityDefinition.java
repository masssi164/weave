package com.massimotter.weave.backend.capability.port;

import com.massimotter.weave.backend.capability.config.CapabilitySettings;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** The capability policy and declarations owned by one installed business domain. */
public record WorkspaceCapabilityDefinition(
        String domainId,
        String category,
        CapabilitySettings settings,
        List<String> capabilityKeys,
        List<String> administratorGrants,
        List<String> memberGrants,
        List<GroupGrant> groupGrants,
        Map<String, String> deniedCapabilityReasons,
        String policyPosture,
        List<String> manifestCapabilities,
        int order) {
    public WorkspaceCapabilityDefinition {
        if (domainId == null || !domainId.matches("[a-z][a-z0-9-]*") || category == null || category.isBlank()) {
            throw new IllegalArgumentException("A stable domain identity and capability category are required");
        }
        Objects.requireNonNull(settings, "settings");
        capabilityKeys = List.copyOf(capabilityKeys);
        administratorGrants = List.copyOf(administratorGrants);
        memberGrants = List.copyOf(memberGrants);
        groupGrants = List.copyOf(groupGrants);
        deniedCapabilityReasons = Map.copyOf(deniedCapabilityReasons);
        manifestCapabilities = List.copyOf(manifestCapabilities);
        var owned = Set.copyOf(capabilityKeys);
        if (!owned.containsAll(administratorGrants) || !owned.containsAll(memberGrants)
                || !owned.containsAll(deniedCapabilityReasons.keySet())
                || groupGrants.stream().anyMatch(grant -> !owned.containsAll(grant.capabilities()))) {
            throw new IllegalArgumentException("Capability grants must belong to their declaring domain");
        }
    }

    public record GroupGrant(String groupPath, String profileKey, List<String> capabilities) {
        public GroupGrant {
            if (groupPath == null || groupPath.isBlank() || profileKey == null || profileKey.isBlank()) {
                throw new IllegalArgumentException("An exact group and policy profile are required");
            }
            capabilities = List.copyOf(capabilities);
        }
    }
}
