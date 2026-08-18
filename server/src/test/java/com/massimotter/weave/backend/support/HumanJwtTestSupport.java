package com.massimotter.weave.backend.support;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Canonical Keycloak native-organization JWT fixtures for human integration tests.
 *
 * <p>The helper deliberately has no top-level role fallback. Tests using it exercise the same
 * selected-organization claim boundary as production.
 */
public final class HumanJwtTestSupport {

    private static final String CLIENT_ID = "weave-app";
    private static final String ORGANIZATION_ALIAS = "weave-dogfood";

    private HumanJwtTestSupport() {}

    public static Map<String, Object> organizationWithRole(String role) {
        return organizationWithRoles(List.of(role));
    }

    public static Map<String, Object> organizationWithRoles(Collection<String> roles) {
        List<String> normalizedRoles = normalized(roles);
        List<String> groups = normalizedRoles.stream()
                .filter(HumanJwtTestSupport::isProductRole)
                .map(role -> "/" + role + "s")
                .toList();
        return organizationWithRolesAndGroups(normalizedRoles, groups);
    }

    public static Map<String, Object> organizationWithRolesAndGroups(
            Collection<String> roles, Collection<String> groups) {
        return Map.of(
                ORGANIZATION_ALIAS,
                Map.of(
                        "groups", normalized(groups),
                        "resource_access",
                        Map.of(CLIENT_ID, Map.of("roles", normalized(roles)))));
    }

    private static boolean isProductRole(String role) {
        return role.equals("owner")
                || role.equals("admin")
                || role.equals("member")
                || role.equals("guest");
    }

    private static List<String> normalized(Collection<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(java.util.Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }
}
