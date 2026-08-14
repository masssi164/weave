package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadPrincipal;
import com.massimotter.weave.backend.agentruntime.port.InvalidRuntimeWorkloadTokenException;
import java.net.URI;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

public final class AgentRuntimeWorkloadTokenPolicy {
    public static final String PROFILE_READ_SCOPE = "agent-runtime.profile.read";
    public static final String PROFILE_READ_AUTHORITY = "SCOPE_" + PROFILE_READ_SCOPE;
    public static final String WORKLOAD_ROLE = "weaver-runtime";

    private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
    private static final Set<String> ALLOWED_REALM_ROLES = Set.of(
            WORKLOAD_ROLE, "default-roles-weave", "offline_access", "uma_authorization");
    private static final Set<String> ALLOWED_ACCOUNT_ROLES = Set.of(
            "manage-account", "manage-account-links", "view-profile");
    private final String requiredAudience;

    public AgentRuntimeWorkloadTokenPolicy(String requiredAudience) {
        URI audience = URI.create(requiredAudience);
        if (!"https".equalsIgnoreCase(audience.getScheme()) || audience.getHost() == null
                || audience.getUserInfo() != null || audience.getQuery() != null
                || audience.getFragment() != null) {
            throw new IllegalArgumentException("Agent Runtime Control audience must be an HTTPS resource URI");
        }
        this.requiredAudience = audience.toString();
    }

    public RuntimeWorkloadPrincipal resolve(Jwt jwt) {
        if (jwt == null || jwt.getIssuer() == null || jwt.getIssuedAt() == null
                || jwt.getExpiresAt() == null || blank(jwt.getId())) {
            throw invalid("missing-standard-claim");
        }
        String clientId = stringClaim(jwt, "client_id");
        String authorizedParty = stringClaim(jwt, "azp");
        if (!CLIENT_ID.matcher(clientId).matches() || !clientId.equals(authorizedParty)) {
            throw invalid("invalid-client-binding");
        }
        String subject = jwt.getSubject();
        if (blank(subject)) {
            throw invalid("missing-workload-subject");
        }
        requireExactAudience(jwt.getAudience());
        requireExactScope(jwt.getClaimAsString("scope"));
        requireAllowedRealmRoles(mapClaim(jwt, "realm_access"));
        requireAllowedClientRoles(mapClaim(jwt, "resource_access"));
        try {
            return new RuntimeWorkloadPrincipal(jwt.getIssuer().toString(), subject, clientId);
        } catch (IllegalArgumentException exception) {
            throw invalid("invalid-workload-principal");
        }
    }

    private void requireExactAudience(List<String> audiences) {
        if (audiences == null || audiences.isEmpty()) {
            throw invalid("invalid-audience");
        }
        Set<String> actual = new HashSet<>(audiences);
        if (actual.size() != audiences.size() || !actual.equals(Set.of(requiredAudience))) {
            throw invalid("invalid-audience");
        }
    }

    private static void requireExactScope(String scopeClaim) {
        if (scopeClaim == null) {
            throw invalid("invalid-scope");
        }
        String[] scopes = scopeClaim.trim().split("\\s+");
        if (scopes.length != 1 || !PROFILE_READ_SCOPE.equals(scopes[0])) {
            throw invalid("invalid-scope");
        }
    }

    private static void requireAllowedRealmRoles(Map<String, Object> realmAccess) {
        if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
            throw invalid("invalid-workload-role");
        }
        Set<String> actual = stringSet(roles, "invalid-workload-role");
        if (!actual.contains(WORKLOAD_ROLE) || !ALLOWED_REALM_ROLES.containsAll(actual)) {
            throw invalid("invalid-workload-role");
        }
    }

    private static void requireAllowedClientRoles(Map<String, Object> resourceAccess) {
        if (resourceAccess == null || resourceAccess.isEmpty()) {
            return;
        }
        if (!resourceAccess.keySet().equals(Set.of("account"))
                || !(resourceAccess.get("account") instanceof Map<?, ?> accountAccess)
                || !accountAccess.keySet().equals(Set.of("roles"))
                || !(accountAccess.get("roles") instanceof Collection<?> roles)) {
            throw invalid("invalid-client-roles");
        }
        Set<String> actual = stringSet(roles, "invalid-client-roles");
        if (!ALLOWED_ACCOUNT_ROLES.containsAll(actual)) {
            throw invalid("invalid-client-roles");
        }
    }

    private static Set<String> stringSet(Collection<?> values, String code) {
        Set<String> result = new HashSet<>();
        for (Object value : values) {
            if (!(value instanceof String text) || text.isBlank() || !result.add(text)) {
                throw invalid(code);
            }
        }
        return Set.copyOf(result);
    }

    private static String stringClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (!(value instanceof String text) || text.isBlank()) {
            throw invalid("missing-" + claim.replace('_', '-'));
        }
        return text;
    }

    private static Map<String, Object> mapClaim(Jwt jwt, String claim) {
        Object value = jwt.getClaims().get(claim);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Map<?, ?> values)) {
            throw invalid("invalid-" + claim.replace('_', '-'));
        }
        Map<String, Object> typed = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key) || entry.getValue() == null) {
                throw invalid("invalid-" + claim.replace('_', '-'));
            }
            typed.put(key, entry.getValue());
        }
        return Map.copyOf(typed);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static InvalidRuntimeWorkloadTokenException invalid(String code) {
        return new InvalidRuntimeWorkloadTokenException(code);
    }
}
