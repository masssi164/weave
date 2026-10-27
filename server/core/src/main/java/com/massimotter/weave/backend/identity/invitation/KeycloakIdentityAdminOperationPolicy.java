package com.massimotter.weave.backend.identity.invitation;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.http.HttpMethod;

/**
 * Closed operation allowlist for the guarded Keycloak identity-lifecycle credential.
 *
 * <p>The provider token is intentionally never exposed outside the identity adapter. Every Admin
 * REST call must match one reviewed method/path pair here before the request reaches the OAuth2
 * {@code RestClient}. The only session operation is member-bound logout during revocation or
 * offboarding; credential, required-action, impersonation, and general session routes have no
 * rule.
 */
public final class KeycloakIdentityAdminOperationPolicy {
  private static final String REALM = "/admin/realms/[^/?]+";
  private static final String ORGANIZATION = REALM + "/organizations/[^/?]+";
  private static final String MEMBER = ORGANIZATION + "/members/[^/?]+";
  private static final String INVITATION = ORGANIZATION + "/invitations/[^/?]+";
  private static final String USER = REALM + "/users/[^/?]+";

  private static final List<Rule> RULES =
      List.of(
          rule(
              "organization-inventory",
              Set.of(HttpMethod.GET),
              REALM + "/organizations\\?first=\\d+&max=\\d+&briefRepresentation=true"),
          rule(
              "human-user-inventory",
              Set.of(HttpMethod.GET),
              REALM + "/users\\?first=0&max=1&briefRepresentation=true"),
          rule(
              "member-inventory",
              Set.of(HttpMethod.GET),
              ORGANIZATION
                  + "/members\\?first=\\d+&max=\\d+&briefRepresentation=(?:true|false)"),
          rule(
              "invitation-create",
              Set.of(HttpMethod.POST),
              ORGANIZATION + "/members/invite-user"),
          rule(
              "invitation-inventory",
              Set.of(HttpMethod.GET),
              ORGANIZATION + "/invitations(?:\\?email=[^#]*)?"),
          rule(
              "invitation-lifecycle",
              Set.of(HttpMethod.POST),
              INVITATION + "/resend"),
          rule(
              "invitation-lifecycle",
              Set.of(HttpMethod.DELETE),
              INVITATION),
          rule(
              "organization-member-groups",
              Set.of(HttpMethod.GET),
              MEMBER + "/groups\\?briefRepresentation=true"),
          rule(
              "organization-member",
              Set.of(HttpMethod.GET, HttpMethod.DELETE),
              MEMBER),
          rule(
              "organization-group",
              Set.of(HttpMethod.GET),
              ORGANIZATION + "/groups\\?search=[^&#]+&exact=true"),
          rule(
              "organization-group-membership",
              Set.of(HttpMethod.PUT, HttpMethod.DELETE),
              ORGANIZATION + "/groups/[^/?]+/members/[^/?]+"),
          rule(
              "member-projection",
              Set.of(HttpMethod.GET, HttpMethod.PUT),
              USER),
          rule(
              "member-session-revocation",
              Set.of(HttpMethod.POST),
              USER + "/logout"));

  private static final List<String> FORBIDDEN_PATH_FRAGMENTS =
      List.of(
          "/reset-password",
          "/credentials",
          "/execute-actions-email",
          "/impersonation",
          "/sessions",
          "/federated-identity",
          "/consents");

  private KeycloakIdentityAdminOperationPolicy() {}

  /** Admits an outgoing operation before transport and defines its one successful status. */
  public static AllowedResponse admit(HttpMethod method, String uri, boolean managedFixture) {
    String operation = managedFixture
        ? requireManagedFixtureAllowed(method, uri)
        : requireAllowed(method, uri);
    int expectedStatus = HttpMethod.GET.equals(method) ? 200 : switch (operation) {
      case "managed-test-identity-create", "managed-test-membership-create" -> 201;
      default -> 204;
    };
    return new AllowedResponse(operation, expectedStatus);
  }

  public record AllowedResponse(String operation, int expectedStatus) {}

  static String requireAllowed(HttpMethod method, String uri) {
    if (method == null || uri == null || uri.isBlank()) {
      throw new IllegalArgumentException("Keycloak identity administration operation is invalid");
    }
    String normalized = uri.toLowerCase(Locale.ROOT);
    if (FORBIDDEN_PATH_FRAGMENTS.stream().anyMatch(normalized::contains)) {
      throw new IllegalArgumentException(
          "Keycloak identity administration operation is outside the guarded boundary");
    }
    return RULES.stream()
        .filter(rule -> rule.methods().contains(method) && rule.path().matcher(uri).matches())
        .map(Rule::operationCode)
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "Keycloak identity administration operation is outside the guarded boundary"));
  }

  /** Separate private-fixture boundary; ordinary identity operations still reject these routes. */
  static String requireManagedFixtureAllowed(HttpMethod method, String uri) {
    List<Rule> rules = List.of(
        rule("managed-test-identity-lookup", Set.of(HttpMethod.GET), REALM + "/users\\?email=[^&#]+&exact=true&first=0&max=2"),
        rule("managed-test-identity-create", Set.of(HttpMethod.POST), REALM + "/users"),
        rule("managed-test-membership-create", Set.of(HttpMethod.POST), ORGANIZATION + "/members"),
        rule("managed-test-initial-credential-read", Set.of(HttpMethod.GET), USER + "/credentials"),
        rule("managed-test-initial-credential-create", Set.of(HttpMethod.PUT), USER + "/reset-password"));
    return rules.stream().filter(rule -> rule.methods().contains(method) && rule.path().matcher(uri).matches())
        .map(Rule::operationCode).findFirst().orElseThrow(() -> new IllegalArgumentException(
            "Keycloak managed test preparation is outside its guarded boundary"));
  }

  private static Rule rule(String operationCode, Set<HttpMethod> methods, String path) {
    return new Rule(operationCode, Set.copyOf(methods), Pattern.compile("^" + path + "$"));
  }

  private record Rule(String operationCode, Set<HttpMethod> methods, Pattern path) {}
}
