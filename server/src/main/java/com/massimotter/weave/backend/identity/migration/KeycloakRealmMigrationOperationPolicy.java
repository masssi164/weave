package com.massimotter.weave.backend.identity.migration;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/** Closed method/path allowlist for the one Keycloak 26.7 FGAP migration operation. */
final class KeycloakRealmMigrationOperationPolicy {
  private static final String REALM = "/admin/realms/weave";
  private static final String MASTER = "/admin/realms/master";
  private static final String ID = "[A-Za-z0-9_-]+";
  private static final String ADMIN_PERMISSIONS =
      REALM + "/clients/" + ID + "/authz/resource-server";
  private static final String POLICY = ADMIN_PERMISSIONS + "/policy/user";
  private static final String PERMISSION = ADMIN_PERMISSIONS + "/permission/scope";

  private static final List<Rule> RULES =
      List.of(
          rule("realm-readback", Set.of(HttpMethod.GET), REALM),
          rule(
              "exact-client-readback",
              Set.of(HttpMethod.GET),
              REALM
                  + "/clients\\?clientId=(?:weave-identity-admin|admin-permissions|realm-management)"),
          rule(
              "bootstrap-client-readback",
              Set.of(HttpMethod.GET),
              MASTER + "/clients\\?clientId=weave-realm-migration-bootstrap"),
          rule(
              "service-account-readback",
              Set.of(HttpMethod.GET),
              "(?:" + REALM + "|" + MASTER + ")/clients/" + ID + "/service-account-user"),
          rule(
              "service-account-role-readback",
              Set.of(HttpMethod.GET),
              "(?:" + REALM + "|" + MASTER + ")/users/" + ID + "/role-mappings"),
          rule(
              "organization-readback",
              Set.of(HttpMethod.GET),
              REALM + "/organizations/" + KeycloakFgapMigrationContract.ORGANIZATION_ID),
          rule(
              "fgap-policy-inventory",
              Set.of(HttpMethod.GET),
              POLICY + "\\?first=\\d+&max=\\d+"),
          rule(
              "fgap-permission-inventory",
              Set.of(HttpMethod.GET),
              PERMISSION + "\\?first=\\d+&max=\\d+"),
          rule("fgap-policy-create", Set.of(HttpMethod.POST), POLICY),
          rule("fgap-policy-update", Set.of(HttpMethod.PUT), POLICY + "/" + ID),
          rule(
              "fgap-policy-dependent-readback",
              Set.of(HttpMethod.GET),
              POLICY + "/" + ID + "/dependentPolicies"),
          rule("fgap-permission-create", Set.of(HttpMethod.POST), PERMISSION),
          rule(
              "fgap-permission-update", Set.of(HttpMethod.PUT), PERMISSION + "/" + ID),
          rule(
              "fgap-permission-relationships",
              Set.of(HttpMethod.GET),
              PERMISSION + "/" + ID + "/(?:resources|scopes|associatedPolicies)"),
          rule(
              "ephemeral-authority-retire",
              Set.of(HttpMethod.DELETE),
              MASTER + "/clients/" + ID));

  private KeycloakRealmMigrationOperationPolicy() {}

  static String requireAllowed(HttpMethod method, String uri) {
    if (method == null || uri == null || uri.isBlank()) {
      throw blocked();
    }
    return RULES.stream()
        .filter(rule -> rule.methods().contains(method) && rule.path().matcher(uri).matches())
        .map(Rule::operationCode)
        .findFirst()
        .orElseThrow(KeycloakRealmMigrationOperationPolicy::blocked);
  }

  private static Rule rule(String code, Set<HttpMethod> methods, String path) {
    return new Rule(code, Set.copyOf(methods), Pattern.compile("^" + path + "$"));
  }

  private static KeycloakRealmMigrationException blocked() {
    return new KeycloakRealmMigrationException("admin-rest-operation-forbidden");
  }

  private record Rule(String operationCode, Set<HttpMethod> methods, Pattern path) {}
}
