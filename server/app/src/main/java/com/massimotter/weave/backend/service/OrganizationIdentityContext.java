package com.massimotter.weave.backend.service;

import java.util.List;

public record OrganizationIdentityContext(
        String organizationId,
        String issuer,
        String subject,
        String primaryIdentityKey,
        String accountId,
        List<String> roles,
        List<String> groups,
        List<String> contextRoles,
        List<String> providerRoleMappings) {

    public OrganizationIdentityContext {
        roles = roles == null ? List.of() : List.copyOf(roles);
        groups = groups == null ? List.of() : List.copyOf(groups);
        contextRoles = contextRoles == null ? List.of() : List.copyOf(contextRoles);
        providerRoleMappings = providerRoleMappings == null ? List.of() : List.copyOf(providerRoleMappings);
    }
}
