package com.massimotter.weave.backend.identity.migration;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Configures only the existing native organization mapper after realm import. */
final class KeycloakOrganizationMapperMigration {
  static final String OPERATION_ID = "organization-membership-id-post-import";
  static final String MUTATION_CODE = "update-native-organization-membership-mapper";
  static final String MAPPER_TYPE = "oidc-organization-membership-mapper";
  static final Map<String, String> REQUIRED_CONFIG = Map.ofEntries(
      Map.entry("claim.name", "organization"),
      Map.entry("jsonType.label", "JSON"),
      Map.entry("multivalued", "true"),
      Map.entry("addOrganizationId", "true"),
      Map.entry("addOrganizationAttributes", "false"),
      Map.entry("addOrganizationDomain", "false"),
      Map.entry("access.token.claim", "true"),
      Map.entry("id.token.claim", "true"),
      Map.entry("userinfo.token.claim", "false"),
      Map.entry("introspection.token.claim", "true"));
  private static final String BASE = "/admin/realms/weave/client-scopes";
  private final KeycloakRealmMigrationTransport transport;

  KeycloakOrganizationMapperMigration(KeycloakRealmMigrationTransport transport) {
    this.transport = transport;
  }

  Plan plan() {
    Snapshot before = read();
    ObjectNode desired = (ObjectNode) before.mappers().get(before.mapperId()).deepCopy();
    ObjectNode config = (ObjectNode) desired.path("config");
    REQUIRED_CONFIG.forEach(config::put);
    return new Plan(before, desired);
  }

  boolean apply(Plan plan) {
    // Fence drift between initial qualification and the eventual bounded write.
    if (!plan.before().equals(read())) {
      throw blocked("organization-mapper-concurrent-change");
    }
    boolean changed = !plan.desired().equals(plan.before().mappers().get(plan.before().mapperId()));
    if (changed) {
      transport.putNoContent(
          BASE + "/" + plan.before().scopeId() + "/protocol-mappers/models/"
              + plan.before().mapperId(), plan.desired());
    }
    requireConverged(plan);
    return changed;
  }

  void requireConverged(Plan plan) {
    Snapshot observed = read();
    Map<String, JsonNode> expectedMappers = new LinkedHashMap<>(plan.before().mappers());
    expectedMappers.put(plan.before().mapperId(), plan.desired());
    if (!observed.scopeId().equals(plan.before().scopeId())
        || !observed.mapperId().equals(plan.before().mapperId())
        || !observed.scope().equals(plan.before().scope())
        || !observed.mappers().equals(expectedMappers)) {
      throw blocked("organization-mapper-readback-mismatch");
    }
    if (!plan().desired().equals(observed.mappers().get(observed.mapperId()))) {
      throw blocked("second-run-plan-not-empty");
    }
  }

  private Snapshot read() {
    JsonNode scopes = transport.get(BASE);
    requireArray(scopes, "organization-scope-inventory-invalid");
    List<JsonNode> matches = scopes.valueStream()
        .filter(scope -> "organization".equals(scope.path("name").asString())).toList();
    if (matches.size() != 1) {
      throw blocked("organization-scope-readback-ambiguous");
    }
    String scopeId = id(matches.getFirst(), "organization-scope-identity-invalid");
    if (!"openid-connect".equals(matches.getFirst().path("protocol").asString())) {
      throw blocked("organization-scope-identity-invalid");
    }
    JsonNode representation = transport.get(BASE + "/" + scopeId);
    if (!scopeId.equals(id(representation, "organization-scope-identity-invalid"))
        || !"organization".equals(representation.path("name").asString())
        || !"openid-connect".equals(representation.path("protocol").asString())) {
      throw blocked("organization-scope-identity-invalid");
    }
    ObjectNode scope = (ObjectNode) representation.deepCopy();
    scope.remove("protocolMappers");
    JsonNode mapperValues = transport.get(BASE + "/" + scopeId + "/protocol-mappers/models");
    requireArray(mapperValues, "organization-mapper-inventory-invalid");
    Map<String, JsonNode> mappers = new LinkedHashMap<>();
    String mapperId = null;
    for (JsonNode value : mapperValues) {
      String currentId = id(value, "organization-mapper-identity-invalid");
      if (mappers.putIfAbsent(currentId, value) != null) {
        throw blocked("organization-mapper-readback-ambiguous");
      }
      if (MAPPER_TYPE.equals(value.path("protocolMapper").asString())) {
        if (mapperId != null) {
          throw blocked("organization-mapper-readback-ambiguous");
        }
        if (!"organization".equals(value.path("name").asString())
            || !"openid-connect".equals(value.path("protocol").asString())
            || !value.path("config").isObject()
            || value.path("config").valueStream().anyMatch(item -> !item.isString())) {
          throw blocked("organization-mapper-identity-invalid");
        }
        mapperId = currentId;
      }
    }
    if (mapperId == null) {
      throw blocked("organization-mapper-readback-ambiguous");
    }
    return new Snapshot(scopeId, mapperId, scope, Map.copyOf(mappers));
  }

  private static void requireArray(JsonNode value, String reason) {
    if (!value.isArray() || value.size() > 1_000) {
      throw blocked(reason);
    }
  }

  private static String id(JsonNode node, String reason) {
    String value = node.path("id").asString();
    if (!node.isObject() || value == null || !value.matches("[A-Za-z0-9_-]{1,128}")) {
      throw blocked(reason);
    }
    return value;
  }

  private static KeycloakRealmMigrationException blocked(String reason) {
    return new KeycloakRealmMigrationException(reason);
  }

  record Snapshot(String scopeId, String mapperId, ObjectNode scope, Map<String, JsonNode> mappers) {}
  record Plan(Snapshot before, ObjectNode desired) {}
}
