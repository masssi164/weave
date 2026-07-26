package com.massimotter.weave.backend.identity.invitation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.KeycloakAdminClientConfiguration;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriUtils;

/**
 * Backend-only anti-corruption layer for Keycloak Organizations.
 *
 * <p>OAuth2 client authentication is owned by the configured {@link RestClient}; this adapter
 * contains no token endpoint calls, bearer-token cache, password flow, or provider credential.
 */
@Component
public class KeycloakIdentityAdminClient {
  private static final Map<String, String> ROLE_GROUP_PATHS =
      Map.of(
          "owner", "/owners",
          "admin", "/admins",
          "member", "/members",
          "guest", "/guests");
  private static final String WEAVER_GROUP_PATH = "/capabilities/weaver";

  private final IdentityInvitationProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private volatile String resolvedOrganizationId;

  public KeycloakIdentityAdminClient(
      IdentityInvitationProperties properties,
      ObjectMapper objectMapper,
      @Qualifier(KeycloakAdminClientConfiguration.KEYCLOAK_ADMIN_REST_CLIENT)
          RestClient restClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  public ProviderInvitation issue(String organizationId, String email, String displayName) {
    Set<String> previousInvitationIds = invitationIds(list(organizationId, email));
    String[] names = splitName(displayName);
    request(
        HttpMethod.POST,
        adminPath("/organizations/" + path(organizationId) + "/members/invite-user"),
        form("email", email, "firstName", names[0], "lastName", names[1]),
        MediaType.APPLICATION_FORM_URLENCODED,
        204);

    List<ProviderInvitation> createdInvitations =
        list(organizationId, email).stream()
            .filter(
                invitation -> !previousInvitationIds.contains(invitation.providerInvitationId()))
            .toList();
    if (createdInvitations.size() != 1) {
      throw new IllegalStateException("Keycloak invitation result was ambiguous");
    }
    return createdInvitations.getFirst();
  }

  public List<ProviderInvitation> list(String organizationId) {
    return list(organizationId, null);
  }

  public List<ProviderInvitation> invitationsForEmail(String organizationId, String email) {
    return list(organizationId, email);
  }

  /**
   * Returns whether the configured organization contains a person.
   *
   * <p>Fresh Weave admits people only through the configured organization. Reading its member
   * projection keeps this check inside the organization-specific FGAP boundary; the identity
   * administration client intentionally has no realm-wide {@code query-users} permission. The
   * bounded pagination fails closed if the complete projection cannot be determined.
   */
  public boolean hasHumanUsers() {
    String organizationId = configuredOrganizationId();
    int pageSize = 100;
    for (int first = 0; first < 1_000; first += pageSize) {
      List<JsonNode> users =
          values(
                  json(
                      request(
                          HttpMethod.GET,
                          adminPath(
                              "/organizations/"
                                  + path(organizationId)
                                  + "/members?first="
                                  + first
                                  + "&max="
                                  + pageSize
                                  + "&briefRepresentation=true"),
                          null,
                          null,
                          200)))
              .toList();
      if (users.stream().anyMatch(user -> !isServiceAccount(user))) {
        return true;
      }
      if (users.size() < pageSize) {
        return false;
      }
    }
    throw new IllegalStateException(
        "Keycloak human-user inventory exceeded the protected bootstrap bound");
  }

  public String configuredOrganizationId() {
    String configuredId = properties.keycloak().organizationId();
    if (!configuredId.isBlank()) {
      return configuredId;
    }
    String cachedId = resolvedOrganizationId;
    if (cachedId != null) {
      return cachedId;
    }

    String alias = properties.keycloak().organizationAlias();
    List<JsonNode> matches = new java.util.ArrayList<>();
    int pageSize = 100;
    boolean inventoryComplete = false;
    for (int first = 0; first < 1_000; first += pageSize) {
      List<JsonNode> page =
          values(
                  json(
                      request(
                          HttpMethod.GET,
                          adminPath(
                              "/organizations?first="
                                  + first
                                  + "&max="
                                  + pageSize
                                  + "&briefRepresentation=true"),
                          null,
                          null,
                          200)))
              .toList();
      page.stream()
          .filter(organization -> alias.equals(organization.path("alias").asString()))
          .forEach(matches::add);
      if (page.size() < pageSize) {
        inventoryComplete = true;
        break;
      }
    }
    if (!inventoryComplete) {
      throw new IllegalStateException(
          "Keycloak organization inventory exceeded the protected lookup bound");
    }
    String resolvedId = exactlyOne(matches, "organization").path("id").asString();
    resolvedOrganizationId = resolvedId;
    return resolvedId;
  }

  public ProviderInvitation resend(String organizationId, String providerInvitationId) {
    request(
        HttpMethod.POST,
        invitationPath(organizationId, providerInvitationId) + "/resend",
        "",
        MediaType.APPLICATION_JSON,
        204);
    return requireInvitation(organizationId, providerInvitationId);
  }

  public void revoke(String organizationId, String providerInvitationId) {
    request(
        HttpMethod.DELETE, invitationPath(organizationId, providerInvitationId), null, null, 204);
  }

  public boolean isOrganizationMember(String organizationId, String subject) {
    try {
      JsonNode member =
          json(
              request(
                  HttpMethod.GET,
                  adminPath("/organizations/" + path(organizationId) + "/members/" + path(subject)),
                  null,
                  null,
                  200));
      return subject.equals(member.path("id").asString());
    } catch (KeycloakAdminException exception) {
      if (exception.status() == 404) {
        return false;
      }
      throw exception;
    }
  }

  /**
   * Returns a bounded live organization-member projection. Keycloak remains authoritative; this
   * method never persists users, memberships, role grants, groups, credentials, or sessions.
   */
  public List<ProviderMember> members(String organizationId) {
    int pageSize = 100;
    List<ProviderMember> members = new java.util.ArrayList<>();
    for (int first = 0; first < 1_000; first += pageSize) {
      List<JsonNode> page =
          values(
                  json(
                      request(
                          HttpMethod.GET,
                          adminPath(
                              "/organizations/"
                                  + path(organizationId)
                                  + "/members?first="
                                  + first
                                  + "&max="
                                  + pageSize
                                  + "&briefRepresentation=false"),
                          null,
                          null,
                          200)))
              .toList();
      for (JsonNode user : page) {
        if (!isServiceAccount(user)) {
          members.add(toMember(organizationId, user));
        }
      }
      if (page.size() < pageSize) {
        return members.stream()
            .sorted(Comparator.comparing(ProviderMember::subject))
            .toList();
      }
    }
    throw new IllegalStateException(
        "Keycloak organization-member inventory exceeded the administration bound");
  }

  public ProviderMember requireMember(String organizationId, String subject) {
    JsonNode user =
        json(
            request(
                HttpMethod.GET,
                adminPath("/users/" + path(subject)),
                null,
                null,
                200));
    if (isServiceAccount(user) || !isOrganizationMember(organizationId, subject)) {
      throw new KeycloakAdminException(404, "Keycloak organization member is unavailable");
    }
    return toMember(organizationId, user);
  }

  public ProviderMember updateMember(
      String organizationId,
      String subject,
      String role,
      boolean enabled) {
    requireMember(organizationId, subject);
    if (!ROLE_GROUP_PATHS.containsKey(role)) {
      throw new IllegalArgumentException("Unsupported Weave product role");
    }
    setExactProductRole(organizationId, subject, role);
    setEnabled(subject, enabled);
    return requireMember(organizationId, subject);
  }

  public ProviderMember setWeaverEntitlement(
      String organizationId, String subject, boolean entitled) {
    requireMember(organizationId, subject);
    setOrganizationGroupMembership(
        organizationId, subject, WEAVER_GROUP_PATH, entitled);
    return requireMember(organizationId, subject);
  }

  public void revokeSessions(String organizationId, String subject) {
    requireMember(organizationId, subject);
    request(
        HttpMethod.POST,
        adminPath("/users/" + path(subject) + "/logout"),
        "",
        MediaType.APPLICATION_JSON,
        204);
  }

  /**
   * Removes every Weave access projection before disabling the identity. The Keycloak user is
   * deliberately retained for audit/recovery and is never hard-deleted.
   */
  public void offboard(String organizationId, String subject) {
    requireMember(organizationId, subject);
    revokeSessions(organizationId, subject);
    request(
        HttpMethod.DELETE,
        adminPath(
            "/organizations/" + path(organizationId) + "/members/" + path(subject)),
        null,
        null,
        204);
    setEnabled(subject, false);
  }

  public void applyRole(String subject, String role) {
    if (!ROLE_GROUP_PATHS.containsKey(role)) {
      throw new IllegalArgumentException("Unsupported Weave product role");
    }
    setExactProductRole(configuredOrganizationId(), subject, role);
  }

  private ProviderMember toMember(String organizationId, JsonNode user) {
    String subject = requiredUser(user, "id");
    Set<String> groupPaths = organizationMemberGroupPaths(organizationId, subject);
    List<String> roles =
        ROLE_GROUP_PATHS.entrySet().stream()
            .filter(entry -> groupPaths.contains(entry.getValue()))
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    List<String> capabilities =
        groupPaths.contains(WEAVER_GROUP_PATH)
            ? List.of("agent-runtime.entitled")
            : List.of();
    String firstName = user.path("firstName").asString("");
    String lastName = user.path("lastName").asString("");
    String displayName = (firstName + " " + lastName).trim();
    return new ProviderMember(
        subject,
        user.path("email").asString(""),
        displayName.isBlank() ? user.path("username").asString("") : displayName,
        roles,
        capabilities,
        user.path("enabled").asBoolean(true));
  }

  private void setExactProductRole(String organizationId, String subject, String role) {
    String requestedPath = ROLE_GROUP_PATHS.get(role);
    Set<String> currentPaths = organizationMemberGroupPaths(organizationId, subject);
    for (String groupPath : ROLE_GROUP_PATHS.values()) {
      boolean requested = requestedPath.equals(groupPath);
      boolean current = currentPaths.contains(groupPath);
      if (requested != current) {
        setOrganizationGroupMembership(organizationId, subject, groupPath, requested);
      }
    }
  }

  private Set<String> organizationMemberGroupPaths(String organizationId, String subject) {
    return values(
            json(
                request(
                    HttpMethod.GET,
                    adminPath(
                        "/organizations/"
                            + path(organizationId)
                            + "/members/"
                            + path(subject)
                            + "/groups?briefRepresentation=true"),
                    null,
                    null,
                    200)))
        .map(group -> group.path("path").asString(""))
        .filter(groupPath -> !groupPath.isBlank())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private void setOrganizationGroupMembership(
      String organizationId, String subject, String groupPath, boolean member) {
    JsonNode group = resolveOrganizationGroup(organizationId, groupPath);
    request(
        member ? HttpMethod.PUT : HttpMethod.DELETE,
        adminPath(
            "/organizations/"
                + path(organizationId)
                + "/groups/"
                + path(group.path("id").asString())
                + "/members/"
                + path(subject)),
        null,
        null,
        204);
  }

  private JsonNode resolveOrganizationGroup(String organizationId, String groupPath) {
    String leaf = groupPath.substring(groupPath.lastIndexOf('/') + 1);
    return exactlyOne(
        values(
                json(
                    request(
                        HttpMethod.GET,
                        adminPath(
                            "/organizations/"
                                + path(organizationId)
                                + "/groups?search="
                                + query(leaf)
                                + "&exact=true"),
                        null,
                        null,
                        200)))
            .filter(candidate -> groupPath.equals(candidate.path("path").asString()))
            .toList(),
        "organization group " + groupPath);
  }

  private void setEnabled(String subject, boolean enabled) {
    JsonNode user =
        json(
            request(
                HttpMethod.GET,
                adminPath("/users/" + path(subject)),
                null,
                null,
                200));
    ((tools.jackson.databind.node.ObjectNode) user).put("enabled", enabled);
    request(
        HttpMethod.PUT,
        adminPath("/users/" + path(subject)),
        user.toString(),
        MediaType.APPLICATION_JSON,
        204);
  }

  private List<ProviderInvitation> list(String organizationId, String email) {
    String filter = email == null ? "" : "?email=" + query(email);
    return values(
            json(
                request(
                    HttpMethod.GET,
                    adminPath("/organizations/" + path(organizationId) + "/invitations" + filter),
                    null,
                    null,
                    200)))
        .filter(node -> email == null || email.equalsIgnoreCase(node.path("email").asString()))
        .map(this::toInvitation)
        .toList();
  }

  private ProviderInvitation requireInvitation(String organizationId, String providerInvitationId) {
    return list(organizationId).stream()
        .filter(invitation -> providerInvitationId.equals(invitation.providerInvitationId()))
        .findFirst()
        .orElseThrow(() -> new KeycloakAdminException(404, "Keycloak invitation is unavailable"));
  }

  private boolean isServiceAccount(JsonNode user) {
    String username = user.path("username").asString("");
    return !user.path("serviceAccountClientId").asString("").isBlank()
        || !user.path("serviceAccountClientLink").asString("").isBlank()
        || username.startsWith("service-account-");
  }

  private ProviderInvitation toInvitation(JsonNode node) {
    String invitationId = required(node, "id");
    String email = required(node, "email");
    String displayName =
        (node.path("firstName").asString("") + " " + node.path("lastName").asString("")).trim();
    String status = node.path("status").asString("pending").toLowerCase(Locale.ROOT);
    return new ProviderInvitation(
        invitationId,
        email,
        displayName.isBlank() ? null : displayName,
        status,
        instant(node, "expiresAt", "expiration"),
        instant(node, "createdAt", "sentDate"));
  }

  private String request(
      HttpMethod method, String uri, String body, MediaType contentType, int expectedStatus) {
    RestClient.RequestBodySpec request = restClient.method(method).uri(uri);
    if (contentType != null) {
      request.contentType(contentType);
    }
    if (body != null) {
      request.body(body);
    }

    try {
      ResponseEntity<String> response =
          request
              .retrieve()
              .onStatus(
                  status -> status.value() != expectedStatus,
                  (ignored, providerResponse) -> {
                    throw new KeycloakAdminException(
                        providerResponse.getStatusCode().value(),
                        "Keycloak administration operation failed",
                        operationCode(method, uri));
                  })
              .toEntity(String.class);
      return response.getBody() == null ? "" : response.getBody();
    } catch (KeycloakAdminException exception) {
      throw exception;
    } catch (RestClientException exception) {
      throw new IllegalStateException("Keycloak administration is unavailable", exception);
    }
  }

  private Instant instant(JsonNode node, String... fieldNames) {
    for (String fieldName : fieldNames) {
      JsonNode value = node.path(fieldName);
      if (value.isIntegralNumber()) {
        long epoch = value.asLong();
        return Instant.ofEpochMilli(epoch < 10_000_000_000L ? epoch * 1000 : epoch);
      }
      if (value.isString() && !value.asString().isBlank()) {
        try {
          return Instant.parse(value.asString());
        } catch (RuntimeException ignored) {
          // Try the next documented Keycloak timestamp representation.
        }
      }
    }
    return null;
  }

  private Set<String> invitationIds(List<ProviderInvitation> invitations) {
    Set<String> ids = new HashSet<>();
    invitations.forEach(invitation -> ids.add(invitation.providerInvitationId()));
    return ids;
  }

  private JsonNode exactlyOne(List<JsonNode> values, String semanticName) {
    if (values.size() != 1 || values.getFirst().path("id").asString().isBlank()) {
      throw new IllegalStateException(
          "Configured Keycloak " + semanticName + " is unavailable or ambiguous");
    }
    return values.getFirst();
  }

  private String required(JsonNode node, String fieldName) {
    String value = node.path(fieldName).asString("");
    if (value.isBlank()) {
      throw new IllegalStateException("Keycloak returned an invalid invitation projection");
    }
    return value;
  }

  private String requiredUser(JsonNode node, String fieldName) {
    String value = node.path(fieldName).asString("");
    if (value.isBlank()) {
      throw new IllegalStateException("Keycloak returned an invalid member projection");
    }
    return value;
  }

  private JsonNode json(String body) {
    try {
      return objectMapper.readTree(body);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Keycloak returned an invalid response", exception);
    }
  }

  private Stream<JsonNode> values(JsonNode node) {
    if (node == null || !node.isArray()) {
      throw new IllegalStateException("Keycloak returned an invalid collection response");
    }
    return StreamSupport.stream(node.spliterator(), false);
  }

  private String adminPath(String suffix) {
    return "/admin/realms/" + path(properties.keycloak().realm()) + suffix;
  }

  private String invitationPath(String organizationId, String invitationId) {
    return adminPath(
        "/organizations/" + path(organizationId) + "/invitations/" + path(invitationId));
  }

  private static String[] splitName(String displayName) {
    if (displayName == null || displayName.isBlank()) {
      return new String[] {"", ""};
    }
    String[] parts = displayName.trim().split("\\s+", 2);
    return new String[] {parts[0], parts.length > 1 ? parts[1] : ""};
  }

  private static String form(String... values) {
    StringBuilder result = new StringBuilder();
    for (int index = 0; index < values.length; index += 2) {
      if (!result.isEmpty()) {
        result.append('&');
      }
      result
          .append(URLEncoder.encode(values[index], StandardCharsets.UTF_8))
          .append('=')
          .append(URLEncoder.encode(values[index + 1], StandardCharsets.UTF_8));
    }
    return result.toString();
  }

  private static String path(String value) {
    return UriUtils.encodePathSegment(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String query(String value) {
    return UriUtils.encodeQueryParam(value == null ? "" : value, StandardCharsets.UTF_8);
  }

  private static String operationCode(HttpMethod method, String uri) {
    if (uri.contains("/members/invite-user")) {
      return "invitation-create";
    }
    if (uri.contains("/invitations")) {
      return method == HttpMethod.GET ? "invitation-inventory" : "invitation-lifecycle";
    }
    if (uri.contains("/members?")) {
      return "member-inventory";
    }
    if (uri.contains("/organizations?")) {
      return "organization-inventory";
    }
    if (uri.contains("/groups")) {
      return "organization-group";
    }
    return "identity-administration";
  }

  public record ProviderInvitation(
      String providerInvitationId,
      String email,
      String displayName,
      String lifecycleStatus,
      Instant expiresAt,
      Instant createdAt) {}

  public record ProviderMember(
      String subject,
      String email,
      String displayName,
      List<String> roles,
      List<String> capabilities,
      boolean enabled) {
    public ProviderMember {
      roles = roles == null ? List.of() : List.copyOf(roles);
      capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
    }
  }

  public static final class KeycloakAdminException extends RuntimeException {
    private final int status;
    private final String operation;

    public KeycloakAdminException(int status, String message) {
      this(status, message, "identity-administration");
    }

    public KeycloakAdminException(int status, String message, String operation) {
      super(message + " with sanitized status " + status);
      this.status = status;
      this.operation = operation;
    }

    public int status() {
      return status;
    }

    public String operation() {
      return operation;
    }
  }
}
