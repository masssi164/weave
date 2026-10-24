package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

class PreparedMemberScenarioTest {
  private final JsonMapper mapper = JsonMapper.builder().build();
  private final MemberTestScenario scenario = MemberTestScenario.resolve(Path.of(".."), "prepared-run");

  @Test
  void acceptsPreparedMembersAndKeepsAdministrativeProbeCredentialsSeparate() {
    PreparedMemberScenario value = require(members(), probes());
    assertEquals("subject-A", value.actorA().identityRef());
    assertEquals("subject-B", value.actorB().identityRef());
    assertEquals("setup-admin", value.administrator().identityRef());
    assertEquals("denied-guest", value.deniedActor().identityRef());
    assertFalse(value.toString().contains("private-password"));
    assertFalse(value.actorA().toString().contains("private-password"));
  }

  @Test
  void rejectsElevatedOrStaleSpaceReadback() {
    for (String field : List.of("organizationRole", "spacePermissions", "grantVersion", "identityRef", "accountRef")) {
      ObjectNode value = members();
      ObjectNode binding = (ObjectNode) value.path("memberBindings").get(0);
      if ("spacePermissions".equals(field)) binding.putArray(field).add("VIEW").add("EDIT").add("ADMIN");
      else binding.put(field, "organizationRole".equals(field) ? "owner" : "invalid");
      assertThrows(ProductFlowException.class, () -> require(value, probes()));
    }
  }

  @Test
  void rejectsAnOwnerOrDeniedProbeReusingEitherMemberIdentity() {
    for (String role : List.of("admin", "denied")) {
      ObjectNode value = probes();
      ((ObjectNode) value.path(role)).put("identityRef", "subject-A");
      assertThrows(ProductFlowException.class, () -> require(members(), value));
    }
    ObjectNode value = members();
    ((ObjectNode) value.path("actors").get(1)).put("identityRef", "subject-A");
    assertThrows(ProductFlowException.class, () -> require(value, probes()));
  }

  @Test
  void rejectsARecipeOrOriginFromAnotherExecution() {
    for (String field : List.of("runId", "organizationId", "publicOrigin", "issuer")) {
      ObjectNode value = members();
      value.put(field, "another-execution");
      assertThrows(ProductFlowException.class, () -> require(value, probes()));
    }
    ObjectNode value = members();
    ((ObjectNode) value.path("memberScenario").path("content")).put("conversation", "local drift");
    assertThrows(ProductFlowException.class, () -> require(value, probes()));
  }

  @Test
  void acceptanceModesAreExplicitAndUnknownModesCannotFallBackToFullCoverage() {
    assertEquals("full", FreshProductFlow.requireFlowMode("full"));
    assertEquals("member-collaboration", FreshProductFlow.requireFlowMode("member-collaboration"));
    for (String mode : List.of("", "member", "admin", "prepared-members")) {
      assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireFlowMode(mode));
    }
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireFlowMode(null));
  }

  private PreparedMemberScenario require(JsonNode members, JsonNode probes) {
    return PreparedMemberScenario.require(members, probes, "prepared-run", "organization",
        URI.create("https://weave.localhost/"), URI.create("https://issuer.localhost/realms/weave"), scenario);
  }

  private ObjectNode members() {
    return mapper.valueToTree(Map.of(
        "schemaVersion", "weave.member-collaboration-input/v1", "runId", "prepared-run",
        "organizationId", "organization", "publicOrigin", "https://weave.localhost",
        "issuer", "https://issuer.localhost/realms/weave", "memberScenario", scenario.resolved(),
        "actors", List.of(actor("A"), actor("B")),
        "memberBindings", List.of(binding("A"), binding("B"))));
  }

  private ObjectNode probes() {
    return mapper.valueToTree(Map.of(
        "schemaVersion", "weave.member-collaboration-probes/v1", "runId", "prepared-run",
        "organizationId", "organization", "admin", probe("setup-admin"), "denied", probe("denied-guest")));
  }

  private static Map<String, String> actor(String slot) {
    return Map.of("slot", slot, "email", "member-" + slot + "@example.invalid",
        "password", "private-password", "identityRef", "subject-" + slot, "accountRef", "account-" + slot);
  }

  private static Map<String, String> probe(String identity) {
    return Map.of("email", identity + "@example.invalid", "password", "private-password", "identityRef", identity);
  }

  private static Map<String, Object> binding(String slot) {
    return Map.of("slot", slot, "organizationId", "organization", "identityRef", "subject-" + slot,
        "accountRef", "account-" + slot, "organizationRole", "member", "spaceRef", "workspace-default",
        "spacePermissions", List.of("VIEW", "EDIT"), "grantVersion", "\"version-" + slot + "\"");
  }
}
