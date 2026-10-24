package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

final class NativeChatFailureDiagnosticTest {
  @TempDir Path temporary;
  private final String candidate = "a".repeat(40);
  private Map<String, Object> snapshot() {
    return new LinkedHashMap<>(Map.of("runId", "owned-run", "candidateCommit", candidate,
        "actorA", Hashing.sha256("actor-A"), "actorB", Hashing.sha256("actor-B"),
        "roomId", "!owned:weave.test"));
  }
  private Path write(Map<String, Object> value) throws Exception {
    Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rwx------"));
    Path path = temporary.toRealPath().resolve("snapshot.json");
    Files.writeString(path, JsonMapper.builder().build().writeValueAsString(value));
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
    return path;
  }
  @Test void onlyOwnedPrivateCurrentRunUiSnapshotIsAdmittedBeforeAnyCommand() throws Exception {
    Path path = write(snapshot());
    assertEquals("!owned:weave.test", FreshProductFlow.requireNativeChatFailureSnapshot(path,
        "owned-run", candidate, "actor-A", "actor-B").path("roomId").asString());
    for (var change : Map.of("runId", "other-run", "candidateCommit", "b".repeat(40),
        "actorA", Hashing.sha256("actor-B"), "actorB", Hashing.sha256("outsider"), "roomId", "../private-secret").entrySet()) {
      var invalid = snapshot(); invalid.put(change.getKey(), change.getValue());
      Path bad = write(invalid);
      assertThrows(IOException.class, () -> FreshProductFlow.requireNativeChatFailureSnapshot(bad,
          "owned-run", candidate, "actor-A", "actor-B"));
    }
    path = write(snapshot());
    Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-r--r--"));
    Path readable = path;
    assertThrows(IOException.class, () -> FreshProductFlow.requireNativeChatFailureSnapshot(readable,
        "owned-run", candidate, "actor-A", "actor-B"));
    Path link = temporary.resolve("link.json"); Files.createSymbolicLink(link, path);
    assertThrows(IOException.class, () -> FreshProductFlow.requireNativeChatFailureSnapshot(link,
        "owned-run", candidate, "actor-A", "actor-B"));
    assertThrows(IOException.class, () -> FreshProductFlow.requireNativeChatFailureSnapshot(temporary.resolve("missing"),
        "owned-run", candidate, "actor-A", "actor-B"));
  }
  @Test void exactDisposableNamespaceAndSourceLabelsAreRequired() throws Exception {
    String project = "weave-e2e-" + "1".repeat(16);
    Map<String, String> labels = new LinkedHashMap<>(Map.of(
        "com.docker.compose.project", project, "com.docker.compose.service", "postgres",
        "com.massimotter.weave.managed", "true", "com.massimotter.weave.environment", "e2e",
        "com.massimotter.weave.scope", "isolated", "com.massimotter.weave.namespace", project,
        "com.massimotter.weave.candidate-commit", candidate));
    var mapper = JsonMapper.builder().build();
    FreshProductFlow.requireNativeDiagnosticContainer(mapper.valueToTree(labels), project, candidate, "postgres");
    for (String key : labels.keySet()) {
      var invalid = new LinkedHashMap<>(labels); invalid.put(key, "other");
      assertThrows(IOException.class, () -> FreshProductFlow.requireNativeDiagnosticContainer(mapper.valueToTree(invalid), project, candidate, "postgres"));
    }
  }
  @Test void onlyFixedCountsAndBooleanCanReachPrivateDiagnostic() throws Exception {
    String diagnostic = FreshProductFlow.nativeChatFailureDiagnostic("1|1|1|0|1|1|f");
    assertTrue(diagnostic.contains("actorBInvitedRows=1"));
    assertTrue(diagnostic.contains("contextMatchesExpectedSpace=false permissionDecision=unavailable"));
    for (String privateData : java.util.List.of("1|1|1|0|1|1|t private-token", "1|1000|1|0|1|1|t",
        "1|1|1|0|1|1|https://private", "0|1|1|0|1|1|t", "1|1|1|0|1|1|t\nprivate-body")) {
      assertThrows(IOException.class, () -> FreshProductFlow.nativeChatFailureDiagnostic(privateData));
    }
  }
  @Test void canonicalMembershipRowsAreObservedWithoutLegacyBindingOrPermissionInference() throws Exception {
    String diagnostic = FreshProductFlow.nativeChatFailureDiagnostic("1|1|1|0|0|1|t");
    assertTrue(diagnostic.contains("personBindingRows=0 activeGrantRows=1"));
    assertTrue(diagnostic.stripTrailing().endsWith("permissionDecision=unavailable"));
  }
  @Test void queryIsReadOnlyAndRejectsControlDataBeforeExecution() throws Exception {
    String sql = FreshProductFlow.nativeChatFailureSql("!owned:weave.test", "organization", "https://issuer.test", "actor-A", "actor-B", "weave.test", "Current shared scenario's room");
    assertTrue(sql.startsWith("BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;"));
    assertTrue(sql.contains("\\if :owned"));
    assertTrue(sql.indexOf("\\if :owned") < sql.indexOf("binding AS"));
    assertTrue(sql.endsWith("ROLLBACK;"));
    assertFalse(sql.contains("permission_set")); // Row observations are not permission decisions.
    assertTrue(sql.contains("c.title='Current shared scenario''s room'"));
    assertThrows(IOException.class, () -> FreshProductFlow.nativeChatFailureSql("!owned:weave.test", "organization", "https://issuer.test", "actor-A\nprivate", "actor-B", "weave.test", "owned-run"));
  }
}
