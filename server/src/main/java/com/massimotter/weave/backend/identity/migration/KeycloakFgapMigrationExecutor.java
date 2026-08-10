package com.massimotter.weave.backend.identity.migration;

import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.ADMIN_PERMISSIONS_CLIENT_ID;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.BOOTSTRAP_REALM_ROLES;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.EXPECTED_PERMISSION_NAMES;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.IDENTITY_ADMIN_CLIENT_ID;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.ORGANIZATION_ALIAS;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.ORGANIZATION_ID;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.ORGANIZATION_PERMISSION_NAME;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.ORGANIZATION_SCOPES;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.POLICY_NAME;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.REALM;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.REALM_MANAGEMENT_CLIENT_ID;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.USERS_PERMISSION_NAME;
import static com.massimotter.weave.backend.identity.migration.KeycloakFgapMigrationContract.USERS_SCOPES;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.StreamSupport;

/** Executes only the deferred organization-specific FGAP operation and retires its authority. */
final class KeycloakFgapMigrationExecutor {
  private static final int PAGE_SIZE = 100;
  private static final int MAXIMUM_RESULTS = 1_000;
  private static final String ADMIN = "/admin/realms/" + REALM;
  private static final String MASTER =
      "/admin/realms/" + KeycloakFgapMigrationContract.BOOTSTRAP_REALM;

  private final KeycloakRealmMigrationTransport transport;
  private final ObjectMapper mapper;

  KeycloakFgapMigrationExecutor(KeycloakRealmMigrationTransport transport, ObjectMapper mapper) {
    this.transport = transport;
    this.mapper = mapper;
  }

  MigrationResult execute(
      KeycloakRealmMigrationManifestReader.MigrationBundle bundle,
      KeycloakRealmMigrationBackupProofReader.BackupProof backupProof) {
    Anchors anchors = requireQualifiedAuthority();

    List<Mutation> firstRun = new ArrayList<>();
    Mutation policyMutation = planPolicy(anchors);
    if (policyMutation != null) {
      apply(policyMutation);
      firstRun.add(policyMutation);
    }
    JsonNode policy = requirePolicy(anchors.identityAdminServiceAccountId());
    requireNoUnexpectedPolicyBindings(anchors.adminPermissionsClientId());
    requirePolicyDependents(policy, false);

    for (PermissionContract permission : permissions()) {
      Mutation mutation = planPermission(anchors, permission);
      if (mutation != null) {
        apply(mutation);
        firstRun.add(mutation);
      }
    }

    requireConverged(anchors);
    retireAndVerifyAuthority(anchors.migrationClientId());
    return new MigrationResult(
        KeycloakFgapMigrationContract.RESULT_SCHEMA,
        "complete",
        bundle.operationId(),
        KeycloakFgapMigrationContract.KEYCLOAK_VERSION,
        bundle.manifestDigest(),
        bundle.bundleDigest(),
        bundle.baselineArtifactDigest(),
        bundle.targetBaselineRevision(),
        backupProof.digest(),
        firstRun.stream().map(Mutation::operationCode).sorted().toList(),
        firstRun.size(),
        true,
        true,
        KeycloakFgapMigrationContract.BOOTSTRAP_REALM,
        KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID,
        true,
        true,
        true,
        false);
  }

  private Anchors requireQualifiedAuthority() {
    JsonNode realm = transport.get(ADMIN);
    if (!realm.isObject()
        || !REALM.equals(realm.path("realm").asString())
        || !realm.path("adminPermissionsEnabled").asBoolean(false)
        || !realm.path("organizationsEnabled").asBoolean(false)) {
      throw blocked("realm-baseline-readback-mismatch");
    }

    JsonNode migrationClient = requireClient(MASTER, MIGRATION_CLIENT_ID);
    requireBootstrapMigrationClient(migrationClient);
    JsonNode migrationAccount =
        requireServiceAccount(MASTER, migrationClient, MIGRATION_CLIENT_ID);
    requireExactMasterBootstrapRole(migrationAccount);

    JsonNode identityAdminClient = requireClient(IDENTITY_ADMIN_CLIENT_ID);
    requirePermanentIdentityAdminShape(identityAdminClient);
    JsonNode identityAdminAccount =
        requireServiceAccount(ADMIN, identityAdminClient, IDENTITY_ADMIN_CLIENT_ID);
    requireExactRoleMappings(
        identityAdminAccount,
        requireClient(REALM_MANAGEMENT_CLIENT_ID),
        Set.of("query-organizations", "query-users"),
        "identity-admin-role-mismatch");

    JsonNode adminPermissions = requireClient(ADMIN_PERMISSIONS_CLIENT_ID);
    String organizationId = requirePrimaryOrganization();
    if (!ORGANIZATION_ID.equals(organizationId)) {
      throw blocked("organization-identity-mismatch");
    }
    return new Anchors(
        requiredId(migrationClient, "migration-client-invalid"),
        requiredId(adminPermissions, "admin-permissions-client-invalid"),
        requiredId(identityAdminAccount, "identity-admin-service-account-invalid"),
        organizationId);
  }

  private static void requireBootstrapMigrationClient(JsonNode client) {
    if (!client.path("enabled").asBoolean(false)
        || client.path("publicClient").asBoolean(true)
        || !client.path("serviceAccountsEnabled").asBoolean(false)
        || client.path("standardFlowEnabled").asBoolean(true)
        || !"client-secret".equals(client.path("clientAuthenticatorType").asString())
        || !"openid-connect".equals(client.path("protocol").asString())) {
      throw blocked("migration-authority-client-shape-mismatch");
    }
    JsonNode attributes = client.path("attributes");
    if (!attributes.isObject()
        || !"true".equals(attributes.path("is_temporary_admin").asString())) {
      throw blocked("migration-authority-temporary-marker-missing");
    }
  }

  private void requireExactMasterBootstrapRole(JsonNode account) {
    String accountId = requiredId(account, "migration-authority-role-mismatch");
    JsonNode mappings = transport.get(MASTER + "/users/" + accountId + "/role-mappings");
    JsonNode clientMappings = mappings.path("clientMappings");
    boolean hasNoClientMappings =
        clientMappings.isMissingNode()
            || clientMappings.isNull()
            || (clientMappings.isObject() && clientMappings.isEmpty());
    if (!mappings.isObject()
        || !BOOTSTRAP_REALM_ROLES.equals(roleNames(mappings.path("realmMappings")))
        || !hasNoClientMappings) {
      throw blocked("migration-authority-role-mismatch");
    }
  }

  private void requirePermanentIdentityAdminShape(JsonNode client) {
    JsonNode attributes = client.path("attributes");
    if (!client.path("enabled").asBoolean(false)
        || client.path("publicClient").asBoolean(true)
        || !client.path("serviceAccountsEnabled").asBoolean(false)
        || client.path("standardFlowEnabled").asBoolean(true)
        || client.path("implicitFlowEnabled").asBoolean(true)
        || client.path("directAccessGrantsEnabled").asBoolean(true)
        || client.path("fullScopeAllowed").asBoolean(true)
        || !"openid-connect".equals(client.path("protocol").asString())
        || !"client-jwt".equals(client.path("clientAuthenticatorType").asString())
        || !client.path("secret").asString("").isBlank()
        || !attributes.isObject()
        || !"private_key_jwt".equals(attributes.path("token.endpoint.auth.method").asString())
        || !"PS256".equals(attributes.path("token.endpoint.auth.signing.alg").asString())
        || !"true".equals(attributes.path("use.jwks.string").asString())
        || !"false".equals(attributes.path("use.jwks.url").asString())) {
      throw blocked("identity-admin-client-shape-mismatch");
    }
    requirePublicIdentityAdminJwks(attributes.path("jwks.string").asString());
  }

  private void requirePublicIdentityAdminJwks(String serializedJwks) {
    JsonNode jwks;
    try {
      jwks = mapper.readTree(serializedJwks);
    } catch (RuntimeException failure) {
      throw blocked("identity-admin-public-jwks-mismatch");
    }
    if (jwks == null
        || !jwks.isObject()
        || !Set.of("keys").equals(fieldNames(jwks))
        || !jwks.path("keys").isArray()
        || jwks.path("keys").size() != 1) {
      throw blocked("identity-admin-public-jwks-mismatch");
    }
    JsonNode key = jwks.path("keys").get(0);
    if (!Set.of("alg", "e", "key_ops", "kid", "kty", "n", "use")
            .equals(fieldNames(key))
        || !"PS256".equals(key.path("alg").asString())
        || !"AQAB".equals(key.path("e").asString())
        || !List.of("verify").equals(stringList(key.path("key_ops")))
        || !key.path("kid").asString().matches("[A-Za-z0-9._-]{1,128}")
        || !"RSA".equals(key.path("kty").asString())
        || !key.path("n").asString().matches("[A-Za-z0-9_-]{64,8192}")
        || !"sig".equals(key.path("use").asString())) {
      throw blocked("identity-admin-public-jwks-mismatch");
    }
  }

  private JsonNode requireClient(String clientId) {
    return requireClient(ADMIN, clientId);
  }

  private JsonNode requireClient(String realmBase, String clientId) {
    List<JsonNode> values =
        array(
            transport.get(realmBase + "/clients?clientId=" + clientId),
            "client-readback-invalid");
    List<JsonNode> matches =
        values.stream().filter(client -> clientId.equals(client.path("clientId").asString())).toList();
    if (matches.size() != 1) {
      throw blocked("client-readback-ambiguous");
    }
    return matches.getFirst();
  }

  private JsonNode requireServiceAccount(
      String realmBase, JsonNode client, String expectedClientId) {
    String clientUuid = requiredId(client, "service-account-client-invalid");
    JsonNode account =
        transport.get(realmBase + "/clients/" + clientUuid + "/service-account-user");
    if (!account.isObject()
        || !account.path("enabled").asBoolean(false)
        || !("service-account-" + expectedClientId).equals(account.path("username").asString())) {
      throw blocked("service-account-readback-mismatch");
    }
    requiredId(account, "service-account-readback-mismatch");
    return account;
  }

  private void requireExactRoleMappings(
      JsonNode account,
      JsonNode realmManagement,
      Set<String> expectedRoles,
      String reasonCode) {
    String accountId = requiredId(account, reasonCode);
    JsonNode mappings = transport.get(ADMIN + "/users/" + accountId + "/role-mappings");
    JsonNode realmMappings = mappings.path("realmMappings");
    if (!mappings.isObject()
        || (!realmMappings.isMissingNode()
            && !realmMappings.isNull()
            && (!realmMappings.isArray() || !realmMappings.isEmpty()))) {
      throw blocked(reasonCode);
    }
    JsonNode clientMappings = mappings.path("clientMappings");
    if (!clientMappings.isObject()
        || clientMappings.size() != 1
        || !clientMappings.has(REALM_MANAGEMENT_CLIENT_ID)) {
      throw blocked(reasonCode);
    }
    JsonNode realmMapping = clientMappings.path(REALM_MANAGEMENT_CLIENT_ID);
    if (!requiredId(realmManagement, reasonCode).equals(realmMapping.path("id").asString())
        || !REALM_MANAGEMENT_CLIENT_ID.equals(realmMapping.path("client").asString())
        || !expectedRoles.equals(roleNames(realmMapping.path("mappings")))) {
      throw blocked(reasonCode);
    }
  }

  private String requirePrimaryOrganization() {
    JsonNode organization = transport.get(ADMIN + "/organizations/" + ORGANIZATION_ID);
    if (!organization.isObject()
        || !ORGANIZATION_ID.equals(organization.path("id").asString())
        || !ORGANIZATION_ALIAS.equals(organization.path("alias").asString())
        || !organization.path("enabled").asBoolean(false)) {
      throw blocked("organization-readback-invalid");
    }
    return ORGANIZATION_ID;
  }

  private Mutation planPolicy(Anchors anchors) {
    List<JsonNode> matches =
        userPolicies(anchors.adminPermissionsClientId()).stream()
            .filter(policy -> POLICY_NAME.equals(policy.path("name").asString()))
            .toList();
    ObjectNode wanted = mapper.createObjectNode();
    wanted.put("name", POLICY_NAME);
    wanted.put("logic", "POSITIVE");
    wanted.putArray("users").add(anchors.identityAdminServiceAccountId());
    if (matches.isEmpty()) {
      return Mutation.create("create-identity-admin-subject-policy", policyBase(anchors), wanted);
    }
    if (matches.size() != 1) {
      throw blocked("identity-admin-policy-ambiguous");
    }
    JsonNode observed = matches.getFirst();
    String id = requiredId(observed, "identity-admin-policy-invalid");
    if (!"user".equals(observed.path("type").asString())
        || !"POSITIVE".equals(observed.path("logic").asString())
        || !Set.of(anchors.identityAdminServiceAccountId())
            .equals(strings(observed.path("users")))) {
      wanted.put("id", id);
      return Mutation.update(
          "update-identity-admin-subject-policy", policyBase(anchors) + "/" + id, wanted);
    }
    return null;
  }

  private JsonNode requirePolicy(String identityAdminServiceAccountId) {
    List<JsonNode> matches =
        userPolicies(requireClientId(ADMIN_PERMISSIONS_CLIENT_ID)).stream()
            .filter(policy -> POLICY_NAME.equals(policy.path("name").asString()))
            .toList();
    if (matches.size() != 1
        || !Set.of(identityAdminServiceAccountId)
            .equals(strings(matches.getFirst().path("users")))) {
      throw blocked("identity-admin-policy-readback-mismatch");
    }
    return matches.getFirst();
  }

  private Mutation planPermission(Anchors anchors, PermissionContract contract) {
    List<JsonNode> matches =
        scopePermissions(anchors.adminPermissionsClientId()).stream()
            .filter(permission -> contract.name().equals(permission.path("name").asString()))
            .toList();
    ObjectNode wanted = permissionPayload(contract, anchors.organizationId());
    if (matches.isEmpty()) {
      return Mutation.create(
          "create-" + contract.operationCode(), permissionBase(anchors) + "/scope", wanted);
    }
    if (matches.size() != 1) {
      throw blocked("identity-admin-permission-ambiguous");
    }
    JsonNode observed = matches.getFirst();
    String id = requiredId(observed, "identity-admin-permission-invalid");
    if (!permissionMatches(
        anchors.adminPermissionsClientId(), observed, contract, anchors.organizationId())) {
      wanted.put("id", id);
      return Mutation.update(
          "update-" + contract.operationCode(),
          permissionBase(anchors) + "/scope/" + id,
          wanted);
    }
    return null;
  }

  private boolean permissionMatches(
      String adminPermissionsClientId,
      JsonNode permission,
      PermissionContract contract,
      String organizationId) {
    String id = requiredId(permission, "identity-admin-permission-invalid");
    String resourceType = permission.path("resourceType").asString();
    if (resourceType.isBlank()) {
      resourceType = permission.path("config").path("defaultResourceType").asString();
    }
    String base = permissionBase(adminPermissionsClientId) + "/scope/" + id;
    Set<String> expectedResources =
        contract.allResources() ? Set.of(contract.resourceType()) : Set.of(organizationId);
    return "scope".equals(permission.path("type").asString())
        && contract.resourceType().equals(resourceType)
        && expectedResources.equals(relationshipNames(base + "/resources"))
        && Set.copyOf(contract.scopes()).equals(relationshipNames(base + "/scopes"))
        && Set.of(POLICY_NAME).equals(relationshipNames(base + "/associatedPolicies"));
  }

  private void requireNoUnexpectedPolicyBindings(String adminPermissionsClientId) {
    for (JsonNode permission : scopePermissions(adminPermissionsClientId)) {
      String id = requiredId(permission, "fgap-permission-inventory-invalid");
      String base = permissionBase(adminPermissionsClientId) + "/scope/" + id;
      Set<String> policies = relationshipNames(base + "/associatedPolicies");
      if (policies.contains(POLICY_NAME)
          && !EXPECTED_PERMISSION_NAMES.contains(permission.path("name").asString())) {
        throw blocked("identity-admin-policy-has-unexpected-binding");
      }
    }
  }

  private void requirePolicyDependents(JsonNode policy, boolean requireComplete) {
    String id = requiredId(policy, "identity-admin-policy-invalid");
    List<JsonNode> dependents =
        array(
            transport.get(policyBase(requireClientId(ADMIN_PERMISSIONS_CLIENT_ID))
                + "/"
                + id
                + "/dependentPolicies"),
            "identity-admin-policy-dependents-invalid");
    Set<String> names = new HashSet<>();
    for (JsonNode dependent : dependents) {
      String name = dependent.path("name").asString();
      if (!"scope".equals(dependent.path("type").asString())
          || !EXPECTED_PERMISSION_NAMES.contains(name)
          || !names.add(name)) {
        throw blocked("identity-admin-policy-has-unexpected-binding");
      }
      requiredId(dependent, "identity-admin-policy-dependents-invalid");
    }
    if (requireComplete && !EXPECTED_PERMISSION_NAMES.equals(names)) {
      throw blocked("identity-admin-policy-dependents-mismatch");
    }
  }

  private void requireConverged(Anchors anchors) {
    if (planPolicy(anchors) != null) {
      throw blocked("second-run-plan-not-empty");
    }
    JsonNode policy = requirePolicy(anchors.identityAdminServiceAccountId());
    for (PermissionContract permission : permissions()) {
      if (planPermission(anchors, permission) != null) {
        throw blocked("second-run-plan-not-empty");
      }
    }
    requireNoUnexpectedPolicyBindings(anchors.adminPermissionsClientId());
    requirePolicyDependents(policy, true);
  }

  private void retireAndVerifyAuthority(String migrationClientId) {
    transport.delete(MASTER + "/clients/" + migrationClientId);
    List<JsonNode> observed =
        array(
            transport.get(MASTER + "/clients?clientId=" + MIGRATION_CLIENT_ID),
            "migration-authority-negative-readback-invalid");
    if (!observed.isEmpty()) {
      throw blocked("migration-authority-still-present");
    }
  }

  private void apply(Mutation mutation) {
    if (mutation.action() == MutationAction.CREATE) {
      transport.post(mutation.path(), mutation.payload());
    } else {
      transport.put(mutation.path(), mutation.payload());
    }
  }

  private List<JsonNode> userPolicies(String adminPermissionsClientId) {
    return paged(policyBase(adminPermissionsClientId), "fgap-policy-inventory-invalid");
  }

  private List<JsonNode> scopePermissions(String adminPermissionsClientId) {
    return paged(
        permissionBase(adminPermissionsClientId) + "/scope",
        "fgap-permission-inventory-invalid");
  }

  private List<JsonNode> paged(String base, String reasonCode) {
    List<JsonNode> values = new ArrayList<>();
    boolean complete = false;
    for (int first = 0; first < MAXIMUM_RESULTS; first += PAGE_SIZE) {
      List<JsonNode> page =
          array(
              transport.get(base + "?first=" + first + "&max=" + PAGE_SIZE), reasonCode);
      values.addAll(page);
      if (page.size() < PAGE_SIZE) {
        complete = true;
        break;
      }
    }
    if (!complete) {
      throw blocked("fgap-inventory-bound-exceeded");
    }
    return List.copyOf(values);
  }

  private Set<String> relationshipNames(String path) {
    List<JsonNode> values = array(transport.get(path), "fgap-relationship-readback-invalid");
    Set<String> names = new HashSet<>();
    for (JsonNode value : values) {
      String name = value.path("name").asString();
      if (name.isBlank() || !names.add(name)) {
        throw blocked("fgap-relationship-readback-invalid");
      }
    }
    return Set.copyOf(names);
  }

  private ObjectNode permissionPayload(PermissionContract contract, String organizationId) {
    ObjectNode result = mapper.createObjectNode();
    result.put("name", contract.name());
    result.put("resourceType", contract.resourceType());
    if (!contract.allResources()) {
      result.putArray("resources").add(organizationId);
    }
    ArrayNode scopes = result.putArray("scopes");
    contract.scopes().forEach(scopes::add);
    result.putArray("policies").add(POLICY_NAME);
    return result;
  }

  private String requireClientId(String clientId) {
    return requiredId(requireClient(clientId), "client-readback-invalid");
  }

  private static List<PermissionContract> permissions() {
    return List.of(
        new PermissionContract(
            ORGANIZATION_PERMISSION_NAME,
            "Organizations",
            false,
            ORGANIZATION_SCOPES,
            "primary-organization-permission"),
        new PermissionContract(
            USERS_PERMISSION_NAME,
            "Users",
            true,
            USERS_SCOPES,
            "users-lifecycle-permission"));
  }

  private static String policyBase(Anchors anchors) {
    return policyBase(anchors.adminPermissionsClientId());
  }

  private static String policyBase(String adminPermissionsClientId) {
    return ADMIN + "/clients/" + adminPermissionsClientId + "/authz/resource-server/policy/user";
  }

  private static String permissionBase(Anchors anchors) {
    return permissionBase(anchors.adminPermissionsClientId());
  }

  private static String permissionBase(String adminPermissionsClientId) {
    return ADMIN + "/clients/" + adminPermissionsClientId + "/authz/resource-server/permission";
  }

  private static List<JsonNode> array(JsonNode value, String reasonCode) {
    if (value == null || !value.isArray()) {
      throw blocked(reasonCode);
    }
    return StreamSupport.stream(value.spliterator(), false).toList();
  }

  private static Set<String> strings(JsonNode value) {
    if (value == null || value.isMissingNode() || value.isNull()) {
      return Set.of();
    }
    if (!value.isArray()) {
      throw blocked("keycloak-readback-shape-invalid");
    }
    Set<String> result = new HashSet<>();
    for (JsonNode item : value) {
      String text = item.asString();
      if (text.isBlank() || !result.add(text)) {
        throw blocked("keycloak-readback-shape-invalid");
      }
    }
    return Set.copyOf(result);
  }

  private static List<String> stringList(JsonNode value) {
    if (!value.isArray()) {
      return List.of();
    }
    List<String> result = new ArrayList<>();
    for (JsonNode item : value) {
      if (!item.isString()) {
        return List.of();
      }
      result.add(item.asString());
    }
    return List.copyOf(result);
  }

  private static Set<String> fieldNames(JsonNode value) {
    if (value == null || !value.isObject()) {
      return Set.of();
    }
    return value.properties().stream()
        .map(Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toUnmodifiableSet());
  }

  private static Set<String> roleNames(JsonNode mappings) {
    if (!mappings.isArray()) {
      throw blocked("role-mapping-readback-invalid");
    }
    Set<String> result = new HashSet<>();
    for (JsonNode mapping : mappings) {
      String name = mapping.path("name").asString();
      if (name.isBlank() || !result.add(name)) {
        throw blocked("role-mapping-readback-invalid");
      }
    }
    return Set.copyOf(result);
  }

  private static String requiredId(JsonNode value, String reasonCode) {
    String id = value.path("id").asString();
    if (id.isBlank() || !id.matches("[A-Za-z0-9_-]+")) {
      throw blocked(reasonCode);
    }
    return id;
  }

  private static KeycloakRealmMigrationException blocked(String code) {
    return new KeycloakRealmMigrationException(code);
  }

  record MigrationResult(
      String schemaVersion,
      String status,
      String operationId,
      String keycloakVersion,
      String manifestDigest,
      String bundleDigest,
      String baselineArtifactDigest,
      String targetBaselineRevision,
      String backupProofDigest,
      List<String> firstRunOperations,
      int firstRunMutationCount,
      boolean semanticReadbackVerified,
      boolean secondRunPlanEmpty,
      String bootstrapAuthorityRealm,
      String bootstrapAuthorityClientId,
      boolean bootstrapAuthorityDeleted,
      boolean bootstrapAuthorityNegativeReadbackVerified,
      boolean supportSafe,
      boolean containsSecretValues) {
    MigrationResult {
      firstRunOperations =
          firstRunOperations == null
              ? List.of()
              : firstRunOperations.stream().sorted(Comparator.naturalOrder()).toList();
    }
  }

  private record Anchors(
      String migrationClientId,
      String adminPermissionsClientId,
      String identityAdminServiceAccountId,
      String organizationId) {}

  private record PermissionContract(
      String name,
      String resourceType,
      boolean allResources,
      List<String> scopes,
      String operationCode) {}

  private enum MutationAction {
    CREATE,
    UPDATE
  }

  private record Mutation(
      MutationAction action, String operationCode, String path, ObjectNode payload) {
    static Mutation create(String code, String path, ObjectNode payload) {
      return new Mutation(MutationAction.CREATE, code, path, payload);
    }

    static Mutation update(String code, String path, ObjectNode payload) {
      return new Mutation(MutationAction.UPDATE, code, path, payload);
    }
  }
}
