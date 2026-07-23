package com.massimotter.weave.mcp;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

final class McpWorkloadTokenPolicy {
    private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
    private static final String WORKLOAD_ROLE = "weaver-runtime";

    private final McpWorkloadProperties properties;

    McpWorkloadTokenPolicy(McpWorkloadProperties properties) {
        this.properties = properties;
    }

    McpCellWorkloadPrincipal resolve(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null || blank(jwt.getId()) || blank(jwt.getSubject())) {
            throw forbidden();
        }
        String type = String.valueOf(jwt.getHeaders().getOrDefault("typ", ""));
        if (!"at+jwt".equalsIgnoreCase(type)) {
            throw forbidden();
        }
        String clientId = stringClaim(jwt, "client_id");
        String authorizedParty = stringClaim(jwt, "azp");
        if (!CLIENT_ID.matcher(clientId).matches() || !clientId.equals(authorizedParty)) {
            throw forbidden();
        }
        Set<String> requiredAudiences = Set.of(properties.resourceUri().toString());
        List<String> audiences = jwt.getAudience();
        if (audiences == null || audiences.size() != requiredAudiences.size()
                || !new HashSet<>(audiences).equals(requiredAudiences)) {
            throw forbidden();
        }
        Set<String> scopes = exactScopes(jwt.getClaimAsString("scope"));
        if (!scopes.equals(Set.copyOf(properties.requiredScopes()))) {
            throw new McpAdmissionException(McpAdmissionException.Kind.INSUFFICIENT_SCOPE);
        }
        requireExactRealmRole(jwt.getClaimAsMap("realm_access"));
        requireNoClientRoles(jwt.getClaimAsMap("resource_access"));
        if (jwt.getExpiresAt().isAfter(jwt.getIssuedAt().plus(properties.maximumTokenTtl()))) {
            throw forbidden();
        }
        return new McpCellWorkloadPrincipal(
                jwt.getIssuer().toString(),
                jwt.getSubject(),
                clientId,
                scopes,
                jwt.getIssuedAt(),
                jwt.getExpiresAt(),
                jwt.getId());
    }

    private static Set<String> exactScopes(String scopeClaim) {
        if (scopeClaim == null || scopeClaim.isBlank()) {
            throw new McpAdmissionException(McpAdmissionException.Kind.INSUFFICIENT_SCOPE);
        }
        String[] values = scopeClaim.trim().split("\\s+");
        LinkedHashSet<String> scopes = new LinkedHashSet<>(List.of(values));
        if (scopes.size() != values.length) {
            throw new McpAdmissionException(McpAdmissionException.Kind.INSUFFICIENT_SCOPE);
        }
        return Set.copyOf(scopes);
    }

    private static void requireExactRealmRole(Map<String, Object> realmAccess) {
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)
                || roles.size() != 1 || !WORKLOAD_ROLE.equals(roles.iterator().next())) {
            throw forbidden();
        }
    }

    private static void requireNoClientRoles(Map<String, Object> resourceAccess) {
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return;
        }
        for (Object access : resourceAccess.values()) {
            if (!(access instanceof Map<?, ?> clientAccess)) {
                throw forbidden();
            }
            Object roles = clientAccess.get("roles");
            if (roles != null && (!(roles instanceof Collection<?> values) || !values.isEmpty())) {
                throw forbidden();
            }
        }
    }

    private static String stringClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (!(value instanceof String text) || text.isBlank()) {
            throw forbidden();
        }
        return text;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static McpAdmissionException forbidden() {
        return new McpAdmissionException(McpAdmissionException.Kind.FORBIDDEN);
    }
}
