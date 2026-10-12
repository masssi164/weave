package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

final class NativeAuthorizationRequestTest {
  @TempDir Path temporary;

  private Map<String, Object> ownedRequest() {
    return new LinkedHashMap<>(Map.of("runId", "owned-run", "spaceRef", "workspace-default",
        "actorA", Hashing.sha256("subject-A"), "actorB", Hashing.sha256("subject-B"),
        "resources", new LinkedHashMap<>(Map.of("roomId", "!owned:weave.localhost", "roomTitle", "Weave UI owned-run",
            "fileId", "file-owned", "fileName", "weave-dual-initial-owned-run.txt",
            "calendarId", "workspace-default", "eventId", "event-owned", "eventTitle", "Calendar UI owned-run updated"))));
  }

  private Path write(Map<String, Object> content) throws Exception {
    Path request = temporary.resolve("request.json");
    Files.writeString(request, JsonMapper.builder().build().writeValueAsString(content));
    Files.setPosixFilePermissions(request, PosixFilePermissions.fromString("rw-------"));
    return request;
  }

  @Test void requiresOwnedRunIdentitiesSpaceAndUiNamesBeforeAnyDenialRequest() throws Exception {
    var content = ownedRequest();
    Path request = write(content);
    assertEquals("owned-run", FreshProductFlow.requireOwnedNativeAuthorizationRequest(request, "owned-run", "subject-A", "subject-B").path("runId").asString());
    for (var entry : Map.of("runId", "foreign-run", "spaceRef", "foreign-space", "actorA", Hashing.sha256("subject-B")).entrySet()) {
      var invalid = ownedRequest(); invalid.put(entry.getKey(), entry.getValue());
      Path path = write(invalid);
      assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(path, "owned-run", "subject-A", "subject-B"));
    }
    for (var entry : Map.of("fileName", "foreign-file.txt", "roomTitle", "foreign room", "eventTitle", "foreign event", "fileId", "../../foreign").entrySet()) {
      var invalid = ownedRequest();
      @SuppressWarnings("unchecked") var resources = (Map<String, Object>) invalid.get("resources");
      resources.put(entry.getKey(), entry.getValue());
      Path path = write(invalid);
      assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(path, "owned-run", "subject-A", "subject-B"));
    }
  }

  @Test void rejectsUnsafePrivatePermissionsSymlinksAndMalformedData() throws Exception {
    Path request = write(ownedRequest());
    Files.setPosixFilePermissions(request, PosixFilePermissions.fromString("rw-r--r--"));
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(request, "owned-run", "subject-A", "subject-B"));
    write(ownedRequest());
    Path alias = temporary.resolve("alias.json"); Files.createSymbolicLink(alias, request);
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(alias, "owned-run", "subject-A", "subject-B"));
    Files.writeString(request, "private-malformed");
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(request, "owned-run", "subject-A", "subject-B"));
    Files.writeString(request, "x".repeat(4097));
    assertThrows(ProductFlowException.class, () -> FreshProductFlow.requireOwnedNativeAuthorizationRequest(request, "owned-run", "subject-A", "subject-B"));
  }
}
