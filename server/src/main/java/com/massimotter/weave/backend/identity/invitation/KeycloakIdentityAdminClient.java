package com.massimotter.weave.backend.identity.invitation;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.config.keycloak.KeycloakAdminClient;
import com.massimotter.weave.backend.config.keycloak.KeycloakAuthConfig;
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
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Backend-only anti-corruption layer for Keycloak Organizations.
 *
 * <p>OAuth2 client authentication is owned by the configured {@link RestClient}; this adapter
 * contains no token endpoint calls, bearer-token cache, password flow, or provider credential.
 */
@Component
public class KeycloakIdentityAdminClient {
  private static final int HUMAN_USER_PROBE_LIMIT = 1;
  private static final Map<String, String> ROLE_GROUP_PATHS =
      Map.of(
          "owner", "/owners",
          "admin", "/admins",
          "member", "/members",
          "guest", "/guests");
  private static final String WEAVER_GROUP_PATH = "/capabilities/weaver";

  private final IdentityInvitationProperties properties;
  private final ObjectMapper objectMapper;
  private final KeycloakAdminClient restClient;
  private volatile String resolvedOrganizationId;

  @Autowired
  public KeycloakIdentityAdminClient(
      IdentityInvitationProperties properties,
      ObjectMapper objectMapper,
      KeycloakAdminClient restClient) {
    this.properties = properties;
    this.objectMapper = objectMapper;
    this.restClient = restClient;
  }

  /** Compatibility seam for focused standalone tests with a stub HTTP transport. */
  KeycloakIdentityAdminClient(
      IdentityInvitationProperties properties, ObjectMapper mapper, RestClient transport) {
    this(properties, mapper, KeycloakAuthConfig.proxy(transport, properties.keycloak().baseUrl()));
  }

  public ProviderInvitation issue(String organizationId, String email, String displayName) {
    Set<String> previousInvitationIds = invitationIds(list(organizationId, email));
    String[] names = splitName(displayName);
    request(() -> restClient.inviteUser(realm(), organizationId,
        form("email", email, "firstName", names[0], "lastName", names[1])));

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
   * Returns whether the realm contains any human identity, including one outside Weave's org.
   *
   * <p>Keycloak's unfiltered user search deliberately excludes service-account users. Adding a
   * user filter changes that provider behavior, so this guarded request admits only pagination
   * and representation-shape parameters and treats every returned identity as human. The bounded
   * one-row projection is sufficient for this bootstrap-presence check and fails closed if the
   * provider ignores the requested bound or omits the stable user identifier.
   */
  public boolean hasHumanUsers() {
    List<JsonNode> users =
        values(
                json(
                    request(() -> restClient.humanUsers(realm()))))
            .toList();
    if (users.size() > HUMAN_USER_PROBE_LIMIT) {
      throw ambiguousHumanUserInventory();
    }
    if (users.isEmpty()) {
      return false;
    }
    requiredBootstrapUserField(users.getFirst(), "id");
    return true;
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
      int offset = first;
      List<JsonNode> page =
          values(
                  json(
                      request(() -> restClient.organizations(realm(), offset, pageSize))))
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
    request(() -> restClient.resendInvitation(realm(), organizationId, providerInvitationId, ""));
    return requireInvitation(organizationId, providerInvitationId);
  }

  public void revoke(String organizationId, String providerInvitationId) {
    request(() -> restClient.revokeInvitation(realm(), organizationId, providerInvitationId));
  }

  public boolean isOrganizationMember(String organizationId, String subject) {
    try {
      JsonNode member =
          json(
              request(() -> restClient.member(realm(), organizationId, subject)));
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
      int offset = first;
      List<JsonNode> page =
          values(
                  json(
                      request(() -> restClient.members(realm(), organizationId, offset, pageSize))))
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

  /** Live selected-organization authority for explicit setup; never a credential or session. */
  public MemberAuthorization memberAuthorization(String organizationId, String subject) {
    JsonNode user = currentMemberUser(organizationId, subject);
    if (isServiceAccount(user) || !user.path("enabled").isBoolean()
        || !user.path("emailVerified").isBoolean() || !user.path("requiredActions").isArray()) {
      throw new IllegalStateException("The live member authorization is invalid");
    }
    boolean activated = user.path("emailVerified").asBoolean(false) && user.path("requiredActions").isEmpty();
    Set<String> groups = organizationMemberGroupPaths(organizationId, subject);
    List<String> roles = ROLE_GROUP_PATHS.entrySet().stream()
        .filter(entry -> groups.contains(entry.getValue())).map(Map.Entry::getKey).sorted().toList();
    return new MemberAuthorization(requiredUser(user, "id"), user.path("enabled").asBoolean(false), activated,
        roles, groups.stream().sorted().toList());
  }

  public record MemberAuthorization(String subject, boolean enabled, boolean activated, List<String> roles, List<String> groups) {
    public MemberAuthorization {
      roles = List.copyOf(roles);
      groups = List.copyOf(groups);
    }
  }

  public ProviderMember requireMember(String organizationId, String subject) {
    return toMember(organizationId, currentMemberUser(organizationId, subject));
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

  public Instant revokeSessions(String organizationId, String subject) {
    requireMember(organizationId, subject);
    request(() -> restClient.logout(realm(), subject, ""));
    JsonNode user = currentMemberUser(organizationId, subject);
    JsonNode notBefore = user.path("notBefore");
    if (!notBefore.isIntegralNumber()) {
      throw invalidLogoutEpoch();
    }
    try {
      long epochSeconds = Long.parseLong(notBefore.toString());
      if (epochSeconds <= 0) {
        throw invalidLogoutEpoch();
      }
      return Instant.ofEpochSecond(epochSeconds);
    } catch (NumberFormatException | java.time.DateTimeException invalid) {
      throw invalidLogoutEpoch();
    }
  }

  /**
   * Removes every Weave access projection before disabling the identity. The Keycloak user is
   * deliberately retained for audit/recovery and is never hard-deleted.
   */
  public Instant offboard(String organizationId, String subject) {
    requireMember(organizationId, subject);
    Instant issuerNotBefore = revokeSessions(organizationId, subject);
    request(() -> restClient.removeMember(realm(), organizationId, subject));
    setEnabled(subject, false);
    return issuerNotBefore;
  }

  public static final Set<String> MANAGED_TEST_STEPS = Set.of("subject", "membership", "credential", "activation", "role");

  /** Durable progress is read from Weave's immutable audit authority, never provider attributes. */
  public record ManagedTestProgress(String accountRef, Set<String> steps) {
    public ManagedTestProgress {
      steps = Set.copyOf(steps);
      if (!MANAGED_TEST_STEPS.containsAll(steps) || (accountRef == null) != steps.isEmpty()
          || (accountRef != null && (!accountRef.matches("acct_[0-9a-f]{32}") || !steps.contains("subject")))) {
        throw new IllegalArgumentException("Managed test progress is invalid");
      }
    }
    public static ManagedTestProgress empty() { return new ManagedTestProgress(null, Set.of()); }
  }
  public record ManagedTestStep(String stage, String subject) {}

  /** Authority is issued only after the Server has matched its immutable operator plan and intent. */
  public record ManagedTestMember(String organizationId, String planId, String slot, String organizationHost,
      String intentRef, String existingSubject, boolean invitationOwned, Instant intentCreatedAt,
      String issuer, ManagedTestProgress progress) {
    public ManagedTestMember {
      if (organizationId == null || organizationId.isBlank() || planId == null || !planId.matches("[0-9a-f]{32}")
          || !List.of("A", "B").contains(slot) || organizationHost == null || !organizationHost.matches("[a-z0-9.-]+")
          || intentRef == null || !intentRef.matches("[0-9a-f-]{36}") || intentCreatedAt == null
          || issuer == null || !issuer.startsWith("https://") || progress == null) {
        throw new IllegalArgumentException("Managed test identity authority is invalid");
      }
    }
    public String email() { return "weave-smoke-" + planId + "-" + slot.toLowerCase(Locale.ROOT) + "@" + organizationHost; }
    public String displayName() { return "Weave test member " + slot + " " + planId.substring(0, 8); }
    public String account(String subject) { return com.massimotter.weave.backend.identity.IdentityReferences.accountId(issuer, subject); }
    ManagedTestMember observed(String stage, String subject) {
      var steps = new HashSet<>(progress.steps()); steps.add(stage);
      return new ManagedTestMember(organizationId, planId, slot, organizationHost, intentRef, existingSubject,
          invitationOwned, intentCreatedAt, issuer, new ManagedTestProgress(account(subject), steps));
    }
  }
  public record ManagedTestIdentity(String subject, boolean created, boolean initialCredentialCreated) {}

  /** Read both planned identities before either is changed; an existing password is never reset. */
  public void verifyManagedTestMember(ManagedTestMember authority) { managedUser(authority, false); }

  public ManagedTestIdentity provisionManagedTestMember(ManagedTestMember authority, String initialPassword,
      java.util.function.Consumer<ManagedTestStep> progress) {
    if (initialPassword == null || initialPassword.length() < 24 || initialPassword.length() > 128
        || initialPassword.chars().anyMatch(Character::isISOControl) || progress == null) {
      throw new IllegalArgumentException("Managed test initial credential is invalid");
    }
    String organizationId = authority.organizationId();
    JsonNode user = managedUser(authority, false);
    boolean created = user == null;
    if (created) {
      String[] names = splitName(authority.displayName());
      var initial = objectMapper.createObjectNode();
      initial.put("username", authority.email()).put("email", authority.email()).put("enabled", true)
          .put("firstName", names[0]).put("lastName", names[1]).put("emailVerified", false);
      initial.putArray("requiredActions").add("UPDATE_PASSWORD");
      request(() -> restClient.createManagedUser(realm(), initial.toString(), true));
      // Only this successful create may read its result before a durable subject binding exists.
      // An uncertain create is never adopted from an address alone on a later invocation.
      user = managedUser(authority, true);
      if (user == null) throw managedConflict();
    }
    String subject = requiredUser(user, "id");
    authority = recordManagedStep(authority, progress, "subject", subject);
    if (!isOrganizationMember(authority.organizationId(), subject)) {
      if (!created || authority.existingSubject() != null || authority.progress().steps().contains("membership")) throw managedConflict();
      request(() -> restClient.addManagedMember(
          realm(), organizationId, objectMapper.writeValueAsString(subject), true));
    }
    authority = recordManagedStep(authority, progress, "membership", subject);
    Set<String> groups = organizationMemberGroupPaths(authority.organizationId(), subject);
    if (groups.stream().anyMatch(group -> !"/members".equals(group))
        || ((authority.existingSubject() != null || authority.progress().steps().contains("role")
              || authority.progress().steps().contains("activation") || managedActivated(user))
            && !groups.equals(Set.of("/members")))) throw managedConflict();
    var credentials = managedCredentials(subject);
    boolean initialCredentialCreated = credentials.isEmpty();
    if (initialCredentialCreated) {
      if (authority.existingSubject() != null || authority.progress().steps().contains("credential")
          || !managedInitialPasswordRequired(user)) throw managedCredentialConflict();
      JsonNode fresh = managedUser(authority, false);
      if (fresh == null || !subject.equals(fresh.path("id").asString()) || !managedInitialPasswordRequired(fresh)
          || !managedCredentials(subject).isEmpty()) throw managedCredentialConflict();
      request(() -> restClient.setManagedInitialPassword(realm(), subject,
          objectMapper.writeValueAsString(Map.of("type", "password", "temporary", false, "value", initialPassword)), true));
    }
    authority = recordManagedStep(authority, progress, "credential", subject);
    if (!user.path("emailVerified").asBoolean(false) || !user.path("requiredActions").isEmpty()) {
      if (authority.existingSubject() != null || authority.progress().steps().contains("activation")) throw managedCredentialConflict();
      JsonNode fresh = managedUser(authority, false);
      if (fresh == null || !subject.equals(fresh.path("id").asString())) throw managedConflict();
      var activated = objectMapper.createObjectNode();
      activated.put("emailVerified", true).putArray("requiredActions");
      request(() -> restClient.updateUser(realm(), subject, activated.toString()));
    }
    authority = recordManagedStep(authority, progress, "activation", subject);
    if (groups.isEmpty()) setOrganizationGroupMembership(authority.organizationId(), subject, "/members", true);
    authority = recordManagedStep(authority, progress, "role", subject);
    var current = memberAuthorization(authority.organizationId(), subject);
    if (!current.enabled() || !current.activated() || !current.roles().equals(List.of("member"))
        || !current.subject().equals(subject) || managedCredentials(subject).size() != 1) throw managedConflict();
    return new ManagedTestIdentity(subject, created, initialCredentialCreated);
  }

  private ManagedTestMember recordManagedStep(ManagedTestMember authority,
      java.util.function.Consumer<ManagedTestStep> progress, String stage, String subject) {
    if (authority.progress().accountRef() != null && !authority.progress().accountRef().equals(authority.account(subject))) throw managedConflict();
    progress.accept(new ManagedTestStep(stage, subject));
    return authority.observed(stage, subject);
  }

  private JsonNode managedUser(ManagedTestMember authority, boolean justCreated) {
    if (!configuredOrganizationId().equals(authority.organizationId())) throw managedConflict();
    var users = values(json(request(() -> restClient.managedUsers(realm(), authority.email(), true)))).toList();
    if (users.isEmpty()) {
      if (authority.existingSubject() != null || authority.progress().accountRef() != null) throw managedConflict();
      return null;
    }
    if (users.size() != 1) throw managedConflict();
    JsonNode user = users.getFirst();
    String subject = requiredUser(user, "id");
    if (!authority.email().equalsIgnoreCase(user.path("email").asString()) || isServiceAccount(user)
        || !user.path("enabled").isBoolean() || !user.path("enabled").asBoolean(false)
        || !user.path("federationLink").asString("").isEmpty()
        || !user.path("requiredActions").isArray() || !user.path("emailVerified").isBoolean()
        || !authority.displayName().equals((user.path("firstName").asString("") + " " + user.path("lastName").asString("")).trim())
        || (authority.existingSubject() != null && !authority.existingSubject().equals(subject))) throw managedConflict();
    for (var action : user.path("requiredActions")) {
      if (!Set.of("UPDATE_PASSWORD", "VERIFY_EMAIL").contains(action.asString())) throw managedCredentialConflict();
    }
    boolean bound = authority.progress().accountRef() != null;
    if (bound && !authority.progress().accountRef().equals(authority.account(subject))) throw managedConflict();
    boolean member = isOrganizationMember(authority.organizationId(), subject);
    if ((authority.existingSubject() != null || bound) && !member) throw managedConflict();
    boolean correlatedInvitation = authority.invitationOwned() && member && user.path("createdTimestamp").isIntegralNumber()
        && user.path("createdTimestamp").asLong() >= authority.intentCreatedAt().toEpochMilli();
    if (!bound && !justCreated && !(member && authority.existingSubject() != null) && !correlatedInvitation) throw managedConflict();
    if (member) {
      Set<String> groups = organizationMemberGroupPaths(authority.organizationId(), subject);
      if (groups.stream().anyMatch(group -> !"/members".equals(group))
          || ((authority.existingSubject() != null || authority.progress().steps().contains("role")
              || authority.progress().steps().contains("activation") || managedActivated(user))
              && !groups.equals(Set.of("/members")))) throw managedConflict();
    }
    var credentials = managedCredentials(subject);
    if ((credentials.isEmpty() && !managedInitialPasswordRequired(user))
        || ((authority.existingSubject() != null || authority.progress().steps().contains("credential")) && credentials.size() != 1)
        || ((authority.existingSubject() != null || authority.progress().steps().contains("activation")
             || (!credentials.isEmpty() && !authority.progress().steps().contains("credential")))
            && (!user.path("emailVerified").asBoolean(false) || !user.path("requiredActions").isEmpty()))) throw managedCredentialConflict();
    return user;
  }

  // Provider activation also protects an identity whose invitation has not yet been reconciled.
  private boolean managedActivated(JsonNode user) {
    return user.path("emailVerified").asBoolean(false) && user.path("requiredActions").isEmpty();
  }

  private boolean managedInitialPasswordRequired(JsonNode user) {
    return values(user.path("requiredActions")).anyMatch(action -> "UPDATE_PASSWORD".equals(action.asString()));
  }

  private List<JsonNode> managedCredentials(String subject) {
    var credentials = values(json(request(() -> restClient.managedCredentials(realm(), subject, true)))).toList();
    if (credentials.size() > 1 || credentials.stream().anyMatch(value -> !"password".equals(value.path("type").asString()))) {
      throw managedCredentialConflict();
    }
    return credentials;
  }
  private IllegalStateException managedConflict() { return new ManagedTestConflict("managed-test-identity-conflict"); }
  private IllegalStateException managedCredentialConflict() { return new ManagedTestConflict("managed-test-credential-conflict"); }
  public static final class ManagedTestConflict extends IllegalStateException {
    public ManagedTestConflict(String boundary) { super(boundary); }
  }

  private JsonNode currentMemberUser(String organizationId, String subject) {
    JsonNode user =
        json(
            request(() -> restClient.user(realm(), subject)));
    if (!subject.equals(user.path("id").asString(""))
        || isServiceAccount(user)
        || !isOrganizationMember(organizationId, subject)) {
      throw new KeycloakAdminException(404, "Keycloak organization member is unavailable");
    }
    return user;
  }

  private KeycloakAdminException invalidLogoutEpoch() {
    return new KeycloakAdminException(
        502, "Keycloak session-revocation metadata is unavailable", "member-session-revocation");
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
                request(() -> restClient.memberGroups(realm(), organizationId, subject))))
        .map(group -> group.path("path").asString(""))
        .filter(groupPath -> !groupPath.isBlank())
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private void setOrganizationGroupMembership(
      String organizationId, String subject, String groupPath, boolean member) {
    JsonNode group = resolveOrganizationGroup(organizationId, groupPath);
    String groupId = group.path("id").asString();
    request(() -> member
        ? restClient.addGroupMember(realm(), organizationId, groupId, subject)
        : restClient.removeGroupMember(realm(), organizationId, groupId, subject));
  }

  private JsonNode resolveOrganizationGroup(String organizationId, String groupPath) {
    String leaf = groupPath.substring(groupPath.lastIndexOf('/') + 1);
    return exactlyOne(
        values(
                json(
                    request(() -> restClient.groups(realm(), organizationId, leaf))))
            .filter(candidate -> groupPath.equals(candidate.path("path").asString()))
            .toList(),
        "organization group " + groupPath);
  }

  private void setEnabled(String subject, boolean enabled) {
    JsonNode user =
        json(
            request(() -> restClient.user(realm(), subject)));
    ((tools.jackson.databind.node.ObjectNode) user).put("enabled", enabled);
    request(() -> restClient.updateUser(realm(), subject, user.toString()));
  }

  private List<ProviderInvitation> list(String organizationId, String email) {
    return values(
            json(
                request(() -> restClient.invitations(realm(), organizationId, email))))
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

  private String request(Supplier<String> operation) {
    try {
      String body = operation.get();
      return body == null ? "" : body;
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

  private String requiredBootstrapUserField(JsonNode node, String fieldName) {
    String value = node.path(fieldName).asString("");
    if (value.isBlank()) {
      throw ambiguousHumanUserInventory();
    }
    return value;
  }

  private IllegalStateException ambiguousHumanUserInventory() {
    return new IllegalStateException(
        "Keycloak human-user inventory is unavailable or ambiguous");
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

  private String realm() {
    return properties.keycloak().realm();
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
