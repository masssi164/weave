package com.massimotter.weave.backend.identity.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KeycloakFgapMigrationExecutorTest {
  private static final String SHA_A =
      "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
  private static final String SHA_B =
      "sha256:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";
  private static final String SHA_C =
      "sha256:cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc";
  private static final String SHA_D =
      "sha256:dddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddddd";

  private final ObjectMapper mapper =
      tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
  private HttpServer server;
  private KeycloakState state;

  @BeforeEach
  void setUp() throws Exception {
    state = new KeycloakState(mapper);
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", state::handle);
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void appliesExactFgapReadsItBackConvergesAndDeletesBootstrapAuthority() {
    KeycloakFgapMigrationExecutor.MigrationResult result =
        executor().execute(bundle(), backupProof());

    assertThat(result.status()).isEqualTo("complete");
    assertThat(result.firstRunMutationCount()).isEqualTo(3);
    assertThat(result.firstRunOperations())
        .containsExactly(
            "create-identity-admin-subject-policy",
            "create-primary-organization-permission",
            "create-users-lifecycle-permission");
    assertThat(result.semanticReadbackVerified()).isTrue();
    assertThat(result.secondRunPlanEmpty()).isTrue();
    assertThat(result.bootstrapAuthorityDeleted()).isTrue();
    assertThat(result.bootstrapAuthorityNegativeReadbackVerified()).isTrue();
    assertThat(result.containsSecretValues()).isFalse();
    assertThat(state.bootstrapPresent).isFalse();
    assertThat(state.forbiddenRequest).isFalse();
  }

  @Test
  void rejectsAnAllOrganizationsBindingToTheIdentityAdminPolicyWithoutMutating() {
    state.seedPolicyAndUnexpectedAllOrganizationsPermission();

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("identity-admin-policy-has-unexpected-binding");

    assertThat(state.bootstrapPresent).isTrue();
    assertThat(state.mutationCount).isZero();
  }

  @Test
  void rejectsBootstrapAuthorityWithAnyAdditionalRole() {
    state.bootstrapHasAdditionalRole = true;

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-authority-role-mismatch");

    assertThat(state.bootstrapPresent).isTrue();
    assertThat(state.mutationCount).isZero();
  }

  @Test
  void rejectsASecretAuthenticatedClientThatIsNotTheQualifiedBootstrapAuthority() {
    state.bootstrapUsesUnexpectedAuthenticator = true;

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-authority-client-shape-mismatch");

    assertThat(state.bootstrapPresent).isTrue();
    assertThat(state.mutationCount).isZero();
  }

  @Test
  void rejectsAHiddenNonScopeDependentOfTheIdentityAdminPolicy() {
    state.seedPolicyAndUnexpectedResourcePermission();

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("identity-admin-policy-has-unexpected-binding");

    assertThat(state.bootstrapPresent).isTrue();
    assertThat(state.mutationCount).isZero();
  }

  @Test
  void rejectsPrivateMaterialInTheIdentityAdminJwks() {
    state.identityJwksContainsPrivateMaterial = true;

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("identity-admin-public-jwks-mismatch");

    assertThat(state.bootstrapPresent).isTrue();
    assertThat(state.mutationCount).isZero();
  }

  @Test
  void blocksCompletionWhenDeletedBootstrapClientStillAppearsInNegativeReadback() {
    state.retainBootstrapInNegativeReadback = true;

    assertThatThrownBy(() -> executor().execute(bundle(), backupProof()))
        .isInstanceOf(KeycloakRealmMigrationException.class)
        .hasMessage("migration-authority-still-present");

    assertThat(state.mutationCount).isEqualTo(3);
  }

  private KeycloakFgapMigrationExecutor executor() {
    RestClient restClient =
        RestClient.builder()
            .baseUrl("http://127.0.0.1:" + server.getAddress().getPort())
            .defaultHeaders(headers -> headers.setBearerAuth("temporary-bootstrap-token"))
            .build();
    return new KeycloakFgapMigrationExecutor(
        new KeycloakRealmMigrationTransport(restClient, mapper), mapper);
  }

  private static KeycloakRealmMigrationManifestReader.MigrationBundle bundle() {
    return new KeycloakRealmMigrationManifestReader.MigrationBundle(
        SHA_A,
        SHA_B,
        SHA_C,
        SHA_D,
        KeycloakFgapMigrationContract.OPERATION_ID,
        SHA_A,
        SHA_B);
  }

  private static KeycloakRealmMigrationBackupProofReader.BackupProof backupProof() {
    return new KeycloakRealmMigrationBackupProofReader.BackupProof(
        SHA_A, "dogfood", "a".repeat(40), "weave-dogfood");
  }

  private static final class KeycloakState {
    private static final String BOOTSTRAP_CLIENT = "bootstrap-client";
    private static final String IDENTITY_CLIENT = "identity-client";
    private static final String ADMIN_PERMISSIONS_CLIENT = "admin-permissions-client";
    private static final String REALM_MANAGEMENT_CLIENT = "realm-management-client";
    private static final String BOOTSTRAP_USER = "bootstrap-user";
    private static final String IDENTITY_USER = "identity-user";

    private final ObjectMapper mapper;
    private final AtomicBoolean forbiddenRequest = new AtomicBoolean();
    private final Map<String, ObjectNode> permissions = new LinkedHashMap<>();
    private boolean bootstrapPresent = true;
    private boolean bootstrapHasAdditionalRole;
    private boolean bootstrapUsesUnexpectedAuthenticator;
    private boolean retainBootstrapInNegativeReadback;
    private boolean identityJwksContainsPrivateMaterial;
    private int mutationCount;
    private ObjectNode policy;
    private ObjectNode unexpectedDependent;

    private KeycloakState(ObjectMapper mapper) {
      this.mapper = mapper;
    }

    private void handle(HttpExchange exchange) throws IOException {
      try {
        if (!"Bearer temporary-bootstrap-token"
            .equals(exchange.getRequestHeaders().getFirst("Authorization"))) {
          respond(exchange, 401, object());
          return;
        }
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();

        if ("GET".equals(method) && "/admin/realms/weave".equals(path)) {
          respond(
              exchange,
              200,
              object(
                  "realm",
                  "weave",
                  "adminPermissionsEnabled",
                  true,
                  "organizationsEnabled",
                  true));
          return;
        }
        if ("GET".equals(method) && path.endsWith("/clients") && query != null) {
          respond(exchange, 200, clients(path, query));
          return;
        }
        if ("GET".equals(method) && path.endsWith("/service-account-user")) {
          if (path.contains(BOOTSTRAP_CLIENT)) {
            respond(
                exchange,
                200,
                object(
                    "id",
                    BOOTSTRAP_USER,
                    "enabled",
                    true,
                    "serviceAccountClientId",
                    KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID));
          } else if (path.contains(IDENTITY_CLIENT)) {
            respond(
                exchange,
                200,
                object(
                    "id",
                    IDENTITY_USER,
                    "enabled",
                    true,
                    "serviceAccountClientId",
                    KeycloakFgapMigrationContract.IDENTITY_ADMIN_CLIENT_ID));
          } else {
            respond(exchange, 404, object());
          }
          return;
        }
        if ("GET".equals(method) && path.endsWith("/role-mappings")) {
          respond(exchange, 200, roleMappings(path));
          return;
        }
        if ("GET".equals(method) && path.endsWith("/organizations")) {
          respond(
              exchange,
              200,
              array(
                  object(
                      "id",
                      KeycloakFgapMigrationContract.ORGANIZATION_ID,
                      "alias",
                      KeycloakFgapMigrationContract.ORGANIZATION_ALIAS)));
          return;
        }
        if (path.endsWith("/policy/user")) {
          if ("GET".equals(method)) {
            respond(exchange, 200, policy == null ? array() : array(policy));
          } else if ("POST".equals(method)) {
            policy = (ObjectNode) readBody(exchange);
            policy.put("id", "identity-policy");
            policy.put("type", "user");
            mutationCount++;
            respond(exchange, 201, policy);
          } else {
            reject(exchange);
          }
          return;
        }
        if (path.contains("/policy/user/") && "PUT".equals(method)) {
          policy = (ObjectNode) readBody(exchange);
          policy.put("type", "user");
          mutationCount++;
          respond(exchange, 201, policy);
          return;
        }
        if (path.contains("/policy/user/")
            && path.endsWith("/dependentPolicies")
            && "GET".equals(method)) {
          ArrayNode dependents = mapper.createArrayNode();
          permissions.values().forEach(
              permission ->
                  dependents.add(
                      object(
                          "id",
                          permission.path("id").asString(),
                          "name",
                          permission.path("name").asString(),
                          "type",
                          permission.path("type").asString())));
          if (unexpectedDependent != null) {
            dependents.add(unexpectedDependent);
          }
          respond(exchange, 200, dependents);
          return;
        }
        if (path.endsWith("/permission/scope")) {
          if ("GET".equals(method)) {
            respond(exchange, 200, array(permissions.values().toArray(JsonNode[]::new)));
          } else if ("POST".equals(method)) {
            ObjectNode permission = (ObjectNode) readBody(exchange);
            permission.put("id", permissionId(permission.path("name").asString()));
            permission.put("type", "scope");
            permissions.put(permission.path("name").asString(), permission);
            mutationCount++;
            respond(exchange, 201, permission);
          } else {
            reject(exchange);
          }
          return;
        }
        if (path.contains("/permission/scope/") && path.endsWith("/resources")) {
          respond(exchange, 200, resources(permission(path)));
          return;
        }
        if (path.contains("/permission/scope/") && path.endsWith("/scopes")) {
          respond(exchange, 200, named(permission(path).path("scopes")));
          return;
        }
        if (path.contains("/permission/scope/") && path.endsWith("/associatedPolicies")) {
          respond(exchange, 200, array(object("name", KeycloakFgapMigrationContract.POLICY_NAME)));
          return;
        }
        if (path.contains("/permission/scope/") && "PUT".equals(method)) {
          ObjectNode permission = (ObjectNode) readBody(exchange);
          permission.put("type", "scope");
          permissions.put(permission.path("name").asString(), permission);
          mutationCount++;
          respond(exchange, 201, permission);
          return;
        }
        if ("DELETE".equals(method)
            && path.equals("/admin/realms/master/clients/" + BOOTSTRAP_CLIENT)) {
          bootstrapPresent = false;
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
          return;
        }
        reject(exchange);
      } catch (RuntimeException failure) {
        respond(exchange, 500, object("failure", "support-safe-test-handler"));
      }
    }

    private ArrayNode clients(String path, String query) {
      String clientId = query.substring("clientId=".length());
      if (path.startsWith("/admin/realms/master")) {
        if (!KeycloakFgapMigrationContract.MIGRATION_CLIENT_ID.equals(clientId)
            || (!bootstrapPresent && !retainBootstrapInNegativeReadback)) {
          return array();
        }
        return array(
            object(
                "id",
                BOOTSTRAP_CLIENT,
                "clientId",
                clientId,
                "enabled",
                true,
                "publicClient",
                false,
                "serviceAccountsEnabled",
                true,
                "standardFlowEnabled",
                false,
                "clientAuthenticatorType",
                bootstrapUsesUnexpectedAuthenticator ? "client-jwt" : "client-secret",
                "protocol",
                "openid-connect",
                "attributes",
                object("is_temporary_admin", "true")));
      }
      return switch (clientId) {
        case KeycloakFgapMigrationContract.IDENTITY_ADMIN_CLIENT_ID ->
            array(
                object(
                    "id",
                    IDENTITY_CLIENT,
                    "clientId",
                    clientId,
                    "enabled",
                    true,
                    "publicClient",
                    false,
                    "serviceAccountsEnabled",
                    true,
                    "standardFlowEnabled",
                    false,
                    "implicitFlowEnabled",
                    false,
                    "directAccessGrantsEnabled",
                    false,
                    "fullScopeAllowed",
                    false,
                    "protocol",
                    "openid-connect",
                    "clientAuthenticatorType",
                    "client-jwt",
                    "secret",
                    "",
                    "attributes",
                    object(
                        "token.endpoint.auth.method",
                        "private_key_jwt",
                        "token.endpoint.auth.signing.alg",
                        "PS256",
                        "use.jwks.string",
                        "true",
                        "use.jwks.url",
                        "false",
                        "jwks.string",
                        identityAdminJwks())));
        case KeycloakFgapMigrationContract.ADMIN_PERMISSIONS_CLIENT_ID ->
            array(object("id", ADMIN_PERMISSIONS_CLIENT, "clientId", clientId));
        case KeycloakFgapMigrationContract.REALM_MANAGEMENT_CLIENT_ID ->
            array(object("id", REALM_MANAGEMENT_CLIENT, "clientId", clientId));
        default -> array();
      };
    }

    private ObjectNode roleMappings(String path) {
      if (path.contains(BOOTSTRAP_USER)) {
        ArrayNode realmMappings = array(object("id", "admin-role", "name", "admin"));
        if (bootstrapHasAdditionalRole) {
          realmMappings.add(object("id", "other-role", "name", "create-realm"));
        }
        return object("realmMappings", realmMappings, "clientMappings", object());
      }
      return object(
          "realmMappings",
          array(),
          "clientMappings",
          object(
              KeycloakFgapMigrationContract.REALM_MANAGEMENT_CLIENT_ID,
              object(
                  "id",
                  REALM_MANAGEMENT_CLIENT,
                  "client",
                  KeycloakFgapMigrationContract.REALM_MANAGEMENT_CLIENT_ID,
                  "mappings",
                  array(
                      object("id", "query-orgs-role", "name", "query-organizations"),
                      object("id", "query-users-role", "name", "query-users")))));
    }

    private ObjectNode permission(String path) {
      String marker = "/permission/scope/";
      String remainder = path.substring(path.indexOf(marker) + marker.length());
      String id = remainder.substring(0, remainder.indexOf('/'));
      return permissions.values().stream()
          .filter(value -> id.equals(value.path("id").asString()))
          .findFirst()
          .orElseThrow();
    }

    private ArrayNode resources(ObjectNode permission) {
      if ("Users".equals(permission.path("resourceType").asString())) {
        return array(object("id", "users-resource", "name", "Users"));
      }
      JsonNode resources = permission.path("resources");
      String organizationId = resources.isArray() && !resources.isEmpty()
          ? resources.get(0).asString()
          : "Organizations";
      return array(object("id", "organization-resource", "name", organizationId));
    }

    private ArrayNode named(JsonNode values) {
      ArrayNode result = mapper.createArrayNode();
      values.forEach(value -> result.add(object("name", value.asString())));
      return result;
    }

    private JsonNode readBody(HttpExchange exchange) throws IOException {
      return mapper.readTree(exchange.getRequestBody().readAllBytes());
    }

    private void seedPolicyAndUnexpectedAllOrganizationsPermission() {
      seedPolicy();
      ObjectNode permission =
          object(
              "id",
              "unexpected-all-organizations",
              "name",
              "unreviewed all organizations",
              "type",
              "scope",
              "resourceType",
              "Organizations",
              "scopes",
              array(
                  mapper.getNodeFactory().stringNode("view"),
                  mapper.getNodeFactory().stringNode("manage")));
      permissions.put(permission.path("name").asString(), permission);
    }

    private void seedPolicyAndUnexpectedResourcePermission() {
      seedPolicy();
      unexpectedDependent =
          object(
              "id",
              "unexpected-resource-permission",
              "name",
              "unreviewed resource permission",
              "type",
              "resource");
    }

    private void seedPolicy() {
      policy =
          object(
              "id",
              "identity-policy",
              "name",
              KeycloakFgapMigrationContract.POLICY_NAME,
              "type",
              "user",
              "logic",
              "POSITIVE",
              "users",
              array(mapper.getNodeFactory().stringNode(IDENTITY_USER)));
    }

    private static String permissionId(String name) {
      return name.contains("primary") ? "organization-permission" : "users-permission";
    }

    private String identityAdminJwks() {
      ObjectNode key =
          object(
              "alg",
              "PS256",
              "e",
              "AQAB",
              "key_ops",
              array(mapper.getNodeFactory().stringNode("verify")),
              "kid",
              "identity-admin-key",
              "kty",
              "RSA",
              "n",
              "A".repeat(342),
              "use",
              "sig");
      if (identityJwksContainsPrivateMaterial) {
        key.put("d", "private-material-must-be-rejected");
      }
      return object("keys", array(key)).toString();
    }

    private ObjectNode object(Object... values) {
      ObjectNode result = mapper.createObjectNode();
      for (int index = 0; index < values.length; index += 2) {
        String name = values[index].toString();
        Object value = values[index + 1];
        if (value instanceof JsonNode json) {
          result.set(name, json);
        } else if (value instanceof Boolean bool) {
          result.put(name, bool);
        } else {
          result.put(name, value.toString());
        }
      }
      return result;
    }

    private ArrayNode array(JsonNode... values) {
      ArrayNode result = mapper.createArrayNode();
      for (JsonNode value : values) {
        result.add(value);
      }
      return result;
    }

    private void reject(HttpExchange exchange) throws IOException {
      forbiddenRequest.set(true);
      respond(exchange, 404, object());
    }

    private void respond(HttpExchange exchange, int status, JsonNode body) throws IOException {
      byte[] bytes = mapper.writeValueAsBytes(body);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, bytes.length);
      exchange.getResponseBody().write(bytes);
      exchange.close();
    }
  }
}
