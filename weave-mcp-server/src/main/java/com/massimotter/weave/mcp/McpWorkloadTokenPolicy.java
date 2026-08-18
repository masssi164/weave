package com.massimotter.weave.mcp;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.oauth2.jwt.Jwt;

final class McpWorkloadTokenPolicy {
  private static final Pattern CLIENT_ID = Pattern.compile("weaver-cell-[A-Za-z0-9_-]+");
  private static final String WORKLOAD_ROLE = "weaver-runtime";
  private static final Set<String> ALLOWED_REALM_ROLES =
      Set.of(WORKLOAD_ROLE, "default-roles-weave", "offline_access", "uma_authorization");
  private static final Set<String> ALLOWED_ACCOUNT_ROLES =
      Set.of("manage-account", "manage-account-links", "view-profile");

  private final McpWorkloadProperties properties;

  McpWorkloadTokenPolicy(McpWorkloadProperties properties) {
    this.properties = properties;
  }

  McpCellWorkloadPrincipal resolve(Jwt jwt) {
    if (jwt == null
        || jwt.getIssuer() == null
        || jwt.getIssuedAt() == null
        || jwt.getExpiresAt() == null
        || blank(jwt.getId())
        || blank(jwt.getSubject())) {
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
    Set<String> requiredAudiences =
        Set.of(properties.resourceUri().toString(), properties.exchangeClientId());
    List<String> audiences = jwt.getAudience();
    if (audiences == null
        || audiences.size() != requiredAudiences.size()
        || !Set.copyOf(audiences).equals(requiredAudiences)) {
      throw forbidden();
    }
    Set<String> scopes = exactScopes(jwt.getClaimAsString("scope"));
    if (!scopes.equals(Set.copyOf(properties.requiredScopes()))) {
      throw new McpAdmissionException(McpAdmissionException.Kind.INSUFFICIENT_SCOPE);
    }
    requireAllowedRealmRoles(jwt.getClaimAsMap("realm_access"));
    requireAllowedClientRoles(jwt.getClaimAsMap("resource_access"));
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

  private static void requireAllowedRealmRoles(Map<String, Object> realmAccess) {
    if (realmAccess == null || !(realmAccess.get("roles") instanceof Collection<?> roles)) {
      throw forbidden();
    }
    Set<String> actual = stringSet(roles);
    if (!actual.contains(WORKLOAD_ROLE) || !ALLOWED_REALM_ROLES.containsAll(actual)) {
      throw forbidden();
    }
  }

  private static void requireAllowedClientRoles(Map<String, Object> resourceAccess) {
    if (resourceAccess == null || resourceAccess.isEmpty()) {
      return;
    }
    if (!resourceAccess.keySet().equals(Set.of("account"))
        || !(resourceAccess.get("account") instanceof Map<?, ?> accountAccess)
        || !accountAccess.keySet().equals(Set.of("roles"))
        || !(accountAccess.get("roles") instanceof Collection<?> roles)
        || !ALLOWED_ACCOUNT_ROLES.containsAll(stringSet(roles))) {
      throw forbidden();
    }
  }

  private static Set<String> stringSet(Collection<?> values) {
    LinkedHashSet<String> result = new LinkedHashSet<>();
    for (Object value : values) {
      if (!(value instanceof String text) || text.isBlank() || !result.add(text)) {
        throw forbidden();
      }
    }
    return Set.copyOf(result);
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
