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
  private static final String PRODUCT_CLIENT_ID = "weave-app";

  private final IdentityInvitationProperties properties;
  private final ObjectMapper objectMapper;
  private final RestClient restClient;
  private volatile String resolvedOrganizationId;
  private volatile String resolvedProductClientUuid;

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
   * Returns whether the realm contains a person rather than a client service account.
   *
   * <p>The bounded pagination fails closed if an unexpectedly large service-account inventory
   * prevents a complete determination.
   */
  public boolean hasHumanUsers() {
    int pageSize = 100;
    for (int first = 0; first < 1_000; first += pageSize) {
      List<JsonNode> users =
          values(
                  json(
                      request(
                          HttpMethod.GET,
                          adminPath(
                              "/users?first="
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
    List<JsonNode> matches =
        values(
                json(
                    request(
                        HttpMethod.GET,
                        adminPath("/organizations?search=" + query(alias) + "&exact=true"),
                        null,
                        null,
                        200)))
            .filter(organization -> alias.equals(organization.path("alias").asString()))
            .toList();
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
      List<String> requestedCapabilities,
      boolean enabled) {
    requireMember(organizationId, subject);
    if (!List.of("owner", "admin", "member", "guest").contains(role)) {
      throw new IllegalArgumentException("Unsupported Weave product role");
    }
    setExactProductRole(subject, role);
    setExactCapabilities(subject, requestedCapabilities);
    setEnabled(subject, enabled);
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
    removeAllProductRoles(subject);
    setExactCapabilities(subject, List.of());
    request(
        HttpMethod.DELETE,
        adminPath(
            "/organizations/" + path(organizationId) + "/members/" + path(subject)),
        null,
        null,
        204);
    setEnabled(subject, false);
  }

  /** Fails before an invitation is issued when a product capability has no exact projection. */
  public void validateCapabilities(List<String> capabilities) {
    projectedGroupNames(capabilities);
  }

  public void applyRoleAndCapabilities(
      String subject, String role, List<String> requestedCapabilities) {
    String productClientUuid = productClientUuid();
    JsonNode roleRepresentation =
        json(
            request(
                HttpMethod.GET,
                adminPath("/clients/" + path(productClientUuid) + "/roles/" + path(role)),
                null,
                null,
                200));
    request(
        HttpMethod.POST,
        adminPath("/users/" + path(subject) + "/role-mappings/clients/" + path(productClientUuid)),
        "[" + roleRepresentation + "]",
        MediaType.APPLICATION_JSON,
        204);

    for (String groupName : projectedGroupNames(requestedCapabilities)) {
      String canonicalPath = canonicalGroupPath(groupName);
      JsonNode group =
          exactlyOne(
              values(
                      json(
                          request(
                              HttpMethod.GET,
                              adminPath("/groups?search=" + query(groupName) + "&exact=true"),
                              null,
                              null,
                              200)))
                  .filter(candidate -> canonicalPath.equals(candidate.path("path").asString()))
                  .toList(),
              "canonical group " + canonicalPath);
      request(
          HttpMethod.PUT,
          adminPath("/users/" + path(subject) + "/groups/" + path(group.path("id").asString())),
          null,
          null,
          204);
    }
  }

  private ProviderMember toMember(String organizationId, JsonNode user) {
    String subject = requiredUser(user, "id");
    List<String> roles = productRoles(subject);
    List<String> capabilities = projectedCapabilities(subject);
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

  private List<String> productRoles(String subject) {
    return values(
            json(
                request(
                    HttpMethod.GET,
                    adminPath(
                        "/users/"
                            + path(subject)
                            + "/role-mappings/clients/"
                            + path(productClientUuid())),
                    null,
                    null,
                    200)))
        .map(role -> role.path("name").asString(""))
        .filter(List.of("owner", "admin", "member", "guest")::contains)
        .distinct()
        .sorted()
        .toList();
  }

  private List<String> projectedCapabilities(String subject) {
    Set<String> groupPaths =
        values(
                json(
                    request(
                        HttpMethod.GET,
                        adminPath("/users/" + path(subject) + "/groups?briefRepresentation=true"),
                        null,
                        null,
                        200)))
            .map(group -> group.path("path").asString(""))
            .collect(java.util.stream.Collectors.toSet());
    return properties.keycloak().capabilityGroupProjections().entrySet().stream()
        .filter(entry -> groupPaths.contains(canonicalGroupPath(entry.getValue())))
        .map(Map.Entry::getKey)
        .sorted()
        .toList();
  }

  private void setExactProductRole(String subject, String role) {
    removeAllProductRoles(subject);
    JsonNode roleRepresentation =
        json(
            request(
                HttpMethod.GET,
                adminPath(
                    "/clients/"
                        + path(productClientUuid())
                        + "/roles/"
                        + path(role)),
                null,
                null,
                200));
    request(
        HttpMethod.POST,
        adminPath(
            "/users/"
                + path(subject)
                + "/role-mappings/clients/"
                + path(productClientUuid())),
        "[" + roleRepresentation + "]",
        MediaType.APPLICATION_JSON,
        204);
  }

  private void removeAllProductRoles(String subject) {
    List<JsonNode> assigned =
        values(
                json(
                    request(
                        HttpMethod.GET,
                        adminPath(
                            "/users/"
                                + path(subject)
                                + "/role-mappings/clients/"
                                + path(productClientUuid())),
                        null,
                        null,
                        200)))
            .filter(
                role ->
                    List.of("owner", "admin", "member", "guest")
                        .contains(role.path("name").asString("")))
            .toList();
    if (!assigned.isEmpty()) {
      request(
          HttpMethod.DELETE,
          adminPath(
              "/users/"
                  + path(subject)
                  + "/role-mappings/clients/"
                  + path(productClientUuid())),
          assigned.toString(),
          MediaType.APPLICATION_JSON,
          204);
    }
  }

  private void setExactCapabilities(String subject, List<String> requestedCapabilities) {
    List<String> requestedGroupNames = projectedGroupNames(requestedCapabilities);
    Map<String, String> groupIds = new java.util.LinkedHashMap<>();
    for (String groupName : properties.keycloak().capabilityGroupProjections().values()) {
      String canonicalPath = canonicalGroupPath(groupName);
      JsonNode group =
          exactlyOne(
              values(
                      json(
                          request(
                              HttpMethod.GET,
                              adminPath("/groups?search=" + query(groupName) + "&exact=true"),
                              null,
                              null,
                              200)))
                  .filter(candidate -> canonicalPath.equals(candidate.path("path").asString()))
                  .toList(),
              "canonical group " + canonicalPath);
      groupIds.put(groupName, group.path("id").asString());
    }
    Set<String> currentGroupIds =
        values(
                json(
                    request(
                        HttpMethod.GET,
                        adminPath("/users/" + path(subject) + "/groups?briefRepresentation=true"),
                        null,
                        null,
                        200)))
            .map(group -> group.path("id").asString(""))
            .filter(value -> !value.isBlank())
            .collect(java.util.stream.Collectors.toSet());
    for (Map.Entry<String, String> group : groupIds.entrySet()) {
      boolean requested = requestedGroupNames.contains(group.getKey());
      boolean current = currentGroupIds.contains(group.getValue());
      if (requested && !current) {
        request(
            HttpMethod.PUT,
            adminPath("/users/" + path(subject) + "/groups/" + path(group.getValue())),
            null,
            null,
            204);
      } else if (!requested && current) {
        request(
            HttpMethod.DELETE,
            adminPath("/users/" + path(subject) + "/groups/" + path(group.getValue())),
            null,
            null,
            204);
      }
    }
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

  private String productClientUuid() {
    String cached = resolvedProductClientUuid;
    if (cached != null) {
      return cached;
    }
    JsonNode productClient =
        exactlyOne(
            values(
                    json(
                        request(
                            HttpMethod.GET,
                            adminPath("/clients?clientId=" + query(PRODUCT_CLIENT_ID)),
                            null,
                            null,
                            200)))
                .filter(client -> PRODUCT_CLIENT_ID.equals(client.path("clientId").asString()))
                .toList(),
            "product client");
    String resolved = productClient.path("id").asString();
    resolvedProductClientUuid = resolved;
    return resolved;
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
                        "Keycloak administration operation failed");
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

  private List<String> projectedGroupNames(List<String> capabilities) {
    if (capabilities == null || capabilities.isEmpty()) {
      return List.of();
    }
    Set<String> requested = new LinkedHashSet<>();
    for (String capability : capabilities) {
      if (capability == null || capability.isBlank()) {
        throw new IllegalArgumentException("A product capability identifier is required");
      }
      requested.add(capability.trim().toLowerCase(Locale.ROOT));
    }

    var projections = properties.keycloak().capabilityGroupProjections();
    return requested.stream()
        .map(
            capability -> {
              String groupName = projections.get(capability);
              if (groupName == null || groupName.isBlank()) {
                throw new IllegalArgumentException(
                    "Unsupported product capability in identity invitation");
              }
              canonicalGroupPath(groupName);
              return groupName;
            })
        .distinct()
        .toList();
  }

  private static String canonicalGroupPath(String configuredGroupName) {
    if (configuredGroupName == null || configuredGroupName.isBlank()) {
      throw new IllegalStateException("A configured Keycloak capability group is required");
    }
    String normalized = configuredGroupName.trim();
    if (normalized.startsWith("/")
        || normalized.contains("/")
        || !normalized.matches("[a-z0-9][a-z0-9._-]{0,159}")) {
      throw new IllegalStateException("Configured Keycloak capability group must be flat");
    }
    return "/" + normalized;
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

    KeycloakAdminException(int status, String message) {
      super(message + " with sanitized status " + status);
      this.status = status;
    }

    public int status() {
      return status;
    }
  }
}
