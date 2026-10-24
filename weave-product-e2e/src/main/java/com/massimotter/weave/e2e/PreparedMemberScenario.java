package com.massimotter.weave.e2e;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Private setup handoff. Nothing here creates users, grants access, or accepts a UI journey. */
record PreparedMemberScenario(String organizationId, Login actorA, Login actorB,
    Login administrator, Login deniedActor) {
  record Login(String email, String password, String identityRef, String accountRef) {
    @Override public String toString() { return "PreparedLogin[private]"; }
  }

  static PreparedMemberScenario read(Path members, Path probes,
      ProductFlowEnvironment environment, MemberTestScenario scenario) {
    return read(members, readPrivate(probes), environment, scenario);
  }

  static PreparedMemberScenario read(Path members, JsonNode probes,
      ProductFlowEnvironment environment, MemberTestScenario scenario) {
    return require(readPrivate(members), probes, environment.runId(),
        environment.tenantId(), environment.productOrigin(), environment.issuer(), scenario);
  }

  static PreparedMemberScenario require(JsonNode members, JsonNode probes, String runId,
      String organizationId, URI origin, URI issuer, MemberTestScenario scenario) {
    if (!members.isObject() || members.size() != 8
        || !"weave.member-collaboration-input/v1".equals(members.path("schemaVersion").asString())
        || !runId.equals(members.path("runId").asString())
        || !organizationId.equals(members.path("organizationId").asString())
        || !origin.toString().replaceAll("/+$", "").equals(members.path("publicOrigin").asString().replaceAll("/+$", ""))
        || !issuer.toString().equals(members.path("issuer").asString())
        || !scenario.resolved().equals(members.path("memberScenario"))
        || !members.path("actors").isArray() || members.path("actors").size() != 2
        || !members.path("memberBindings").isArray() || members.path("memberBindings").size() != 2
        || !probes.isObject() || probes.size() != 5
        || !"weave.member-collaboration-probes/v1".equals(probes.path("schemaVersion").asString())
        || !runId.equals(probes.path("runId").asString())
        || !organizationId.equals(probes.path("organizationId").asString())) {
      throw new ProductFlowException("Prepared member setup is not bound to this execution");
    }
    Login a = actor(members.path("actors").get(0), "A");
    Login b = actor(members.path("actors").get(1), "B");
    Login administrator = login(probes.path("admin"), false);
    Login denied = login(probes.path("denied"), false);
    if (a.identityRef().equals(b.identityRef()) || a.accountRef().equals(b.accountRef())) {
      throw new ProductFlowException("Prepared collaboration needs two independent members");
    }
    Set<String> subjects = new HashSet<>();
    Set<String> emails = new HashSet<>();
    for (Login actor : List.of(a, b, administrator, denied)) {
      if (!subjects.add(actor.identityRef()) || !emails.add(actor.email().toLowerCase(java.util.Locale.ROOT))) {
        throw new ProductFlowException("Prepared setup and denial actors must remain separate from members");
      }
    }
    for (int index = 0; index < 2; index++) {
      Login actor = index == 0 ? a : b;
      JsonNode binding = members.path("memberBindings").get(index);
      if (!binding.isObject() || binding.size() != 8
          || !List.of("A", "B").get(index).equals(binding.path("slot").asString())
          || !organizationId.equals(binding.path("organizationId").asString())
          || !actor.identityRef().equals(binding.path("identityRef").asString())
          || !actor.accountRef().equals(binding.path("accountRef").asString())
          || !"member".equals(binding.path("organizationRole").asString())
          || !scenario.spaceRef().equals(binding.path("spaceRef").asString())
          || !binding.path("spacePermissions").isArray() || binding.path("spacePermissions").size() != 2
          || !"VIEW".equals(binding.path("spacePermissions").get(0).asString())
          || !"EDIT".equals(binding.path("spacePermissions").get(1).asString())
          || !binding.path("grantVersion").asString().matches("\"[^\"\\r\\n]+\"")) {
        throw new ProductFlowException("Prepared member access readback is invalid");
      }
    }
    return new PreparedMemberScenario(organizationId, a, b, administrator, denied);
  }

  private static Login actor(JsonNode actor, String slot) {
    if (!slot.equals(actor.path("slot").asString())) {
      throw new ProductFlowException("Prepared member slot is invalid");
    }
    return login(actor, true);
  }

  private static Login login(JsonNode actor, boolean member) {
    if (!actor.isObject() || actor.size() != (member ? 5 : 3)) {
      throw new ProductFlowException("Prepared actor input is invalid");
    }
    for (String key : member ? List.of("email", "password", "identityRef", "accountRef")
        : List.of("email", "password", "identityRef")) {
      if (!actor.path(key).isString() || actor.path(key).asString().isBlank()
          || actor.path(key).asString().length() > 512
          || actor.path(key).asString().chars().anyMatch(value -> value < 32)) {
        throw new ProductFlowException("Prepared actor input is invalid");
      }
    }
    return new Login(actor.path("email").asString(), actor.path("password").asString(),
        actor.path("identityRef").asString(), member ? actor.path("accountRef").asString() : "");
  }

  private static JsonNode readPrivate(Path file) {
    Path path = file.toAbsolutePath().normalize();
    try {
      if (Files.isSymbolicLink(path) || Files.isSymbolicLink(path.getParent())
          || !path.getParent().toRealPath().equals(path.getParent())
          || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path) > 32_768
          || !Files.getOwner(path).equals(Files.getOwner(path.getParent()))
          || !Files.getOwner(path.getParent()).equals(Files.getOwner(Path.of(".").toAbsolutePath()))
          || !Files.getPosixFilePermissions(path).equals(PosixFilePermissions.fromString("rw-------"))
          || !Files.getPosixFilePermissions(path.getParent()).equals(PosixFilePermissions.fromString("rwx------"))) {
        throw new ProductFlowException("Prepared member input must remain private and owned");
      }
      return JsonMapper.builder().build().readTree(Files.readAllBytes(path));
    } catch (IOException | JacksonException failure) {
      throw new ProductFlowException("Prepared member input is unavailable");
    }
  }

  @Override public String toString() { return "PreparedMemberScenario[private]"; }
}
