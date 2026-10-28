package com.massimotter.weave.backend.admin.application;

import com.massimotter.weave.backend.admin.domain.AdminCapabilityDeclaration;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared policy merges explicit installed declarations; no absent feature receives a grant. */
public final class AdminCapabilityCatalog {
    private final List<AdminCapabilityDeclaration> declarations;
    private final Set<String> known;

    public AdminCapabilityCatalog(List<AdminCapabilityDeclaration> declarations) {
        this.declarations = declarations.stream().sorted(Comparator.comparingInt(AdminCapabilityDeclaration::order)
                .thenComparing(AdminCapabilityDeclaration::owner)).toList();
        var owners = new LinkedHashSet<String>();
        var capabilities = new LinkedHashSet<String>();
        for (var declaration : this.declarations) {
            if (!owners.add(declaration.owner())) throw new IllegalArgumentException("Duplicate Admin capability owner");
            for (String capability : declaration.knownCapabilities()) {
                if (!capabilities.add(capability)) throw new IllegalArgumentException("Duplicate Admin capability ownership");
            }
            var granted = new LinkedHashSet<>(declaration.administratorGrants());
            granted.addAll(declaration.operatorGrants());
            granted.addAll(declaration.memberGrants());
            declaration.profiles().forEach(profile -> granted.addAll(profile.capabilities()));
            if (!declaration.knownCapabilities().containsAll(granted)) {
                throw new IllegalArgumentException("Admin grants must belong to their declaring owner");
            }
        }
        this.known = Set.copyOf(capabilities);
    }

    public Set<String> knownCapabilities() {
        return known;
    }

    public Set<String> grants(List<String> roles) {
        var grants = new LinkedHashSet<String>();
        for (var declaration : declarations) {
            if (roles.contains("owner") || roles.contains("admin")) grants.addAll(declaration.administratorGrants());
            if (roles.contains("operator")) grants.addAll(declaration.operatorGrants());
            if (roles.contains("member")) grants.addAll(declaration.memberGrants());
        }
        return grants;
    }

    public Map<String, List<String>> profiles() {
        var profiles = new LinkedHashMap<String, List<String>>();
        for (String profile : List.of("workspace-admin", "workspace-operator", "member-default", "guest-deny-default",
                "group:/capabilities/weaver")) {
            profiles.put(profile, declarations.stream().flatMap(value -> value.profiles().stream())
                    .filter(value -> value.profile().equals(profile))
                    .sorted(Comparator.comparingInt(AdminCapabilityDeclaration.ProfileGrant::order))
                    .flatMap(value -> value.capabilities().stream()).distinct().toList());
        }
        return profiles;
    }
}
