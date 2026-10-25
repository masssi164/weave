package com.massimotter.weave.backend.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Closed parser for Keycloak's selected native-organization access-token projection.
 *
 * <p>Human roles and groups are organization-bound. Top-level group and client-role claims are
 * intentionally not fallbacks because they lose the organization boundary.
 */
public final class NativeOrganizationClaims {

    private NativeOrganizationClaims() {}

    public static List<String> groups(Jwt jwt) {
        Map<?, ?> organization = selectedOrganization(jwt);
        return organization == null ? List.of() : strings(organization.get("groups"));
    }

    public static List<String> clientRoles(Jwt jwt, String clientId) {
        Map<?, ?> organization = selectedOrganization(jwt);
        if (organization == null
                || !(organization.get("resource_access") instanceof Map<?, ?> resourceAccess)
                || !(resourceAccess.get(clientId) instanceof Map<?, ?> clientAccess)) {
            return List.of();
        }
        return strings(clientAccess.get("roles"));
    }

    private static Map<?, ?> selectedOrganization(Jwt jwt) {
        if (jwt == null
                || !(jwt.getClaims().get("organization") instanceof Map<?, ?> organizations)
                || organizations.size() != 1) {
            return null;
        }
        Object selected = organizations.values().iterator().next();
        return selected instanceof Map<?, ?> organization ? organization : null;
    }

    private static List<String> strings(Object value) {
        if (!(value instanceof Collection<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }
}
