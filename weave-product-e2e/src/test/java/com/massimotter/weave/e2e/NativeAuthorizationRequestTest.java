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

  @Test void requiresRealConditionalMutationsAndPreservesTheProtectedResources() throws Exception {
    try (var fixture = new MutationFixture()) {
      fixture.verify();
      var writes = fixture.requests.stream().filter(request -> !request.method().equals("GET")).toList();
      assertEquals(3, writes.size());
      assertEquals(java.util.List.of("PUT", "PUT", "DELETE"), writes.stream().map(MutationRequest::method).toList());
      assertEquals(java.util.List.of("/_matrix/client/v3/rooms/!owned:weave.localhost/send/m.room.encrypted/native-denied-owned-run",
          "/api/files/items/file-owned/content", "/api/calendar/calendars/workspace-default/events/event-owned"),
          writes.stream().map(MutationRequest::path).toList());
      for (var request : writes) assertEquals("Bearer denied-fixture", request.authorization());
      assertEquals("m.megolm.v1.aes-sha2", JsonMapper.builder().build().readTree(writes.get(0).body()).path("algorithm").asString());
      assertEquals("\"file-current\"", writes.get(1).version());
      assertEquals("native-denied-owned-run", writes.get(1).idempotencyKey());
      assertEquals("application/octet-stream", writes.get(1).contentType());
      assertEquals("\"event-current\"", writes.get(2).version());
      assertEquals(4, fixture.requests.stream().filter(request -> request.method().equals("GET")).count());
      for (var request : fixture.requests.stream().filter(request -> request.method().equals("GET")).toList()) {
        assertEquals("Bearer authorized-fixture", request.authorization());
      }
    }
  }

  @Test void rejectsSuccessfulMutationsAndUnrelatedDenialCodes() throws Exception {
    for (String category : java.util.List.of("chat", "files", "calendar")) {
      try (var fixture = new MutationFixture()) {
        fixture.invalidCategory = category;
        fixture.mutationStatus = 200;
        assertThrows(ProductFlowException.class, fixture::verify);
      }
      try (var fixture = new MutationFixture()) {
        fixture.invalidCategory = category;
        fixture.invalidDenialCode = true;
        assertThrows(ProductFlowException.class, fixture::verify);
      }
    }
  }

  @Test void denialCategoryDiagnosticsReportOnlyKnownCodes() throws Exception {
    for (String actual : java.util.List.of("files-forbidden", "", "private-denial-payload\nnot-a-public-code")) {
      try (var fixture = new MutationFixture()) {
        fixture.invalidCategory = "files";
        fixture.invalidDenialCode = true;
        fixture.unexpectedDenialCode = actual;
        ProductFlowException failure = assertThrows(ProductFlowException.class, fixture::verify);
        String safeActual = actual.equals("files-forbidden") ? actual : actual.isEmpty() ? "missing" : "unrecognized";
        assertEquals("native files mutation denial category was invalid"
            + " (HTTP 403 expected=capability-policy-blocked observed=" + safeActual + ")", failure.getMessage());
      }
    }
  }

  @Test void missingWeakAndAmbiguousCurrentVersionsCannotSatisfyAuthorization() throws Exception {
    for (String invalid : java.util.List.of("", "W/\"weak\"", "*", "\"one\",\"two\"")) {
      for (String category : java.util.List.of("files", "calendar")) {
        try (var fixture = new MutationFixture()) {
          if (category.equals("files")) fixture.fileVersion = invalid;
          else fixture.eventVersion = invalid;
          assertThrows(ProductFlowException.class, fixture::verify);
          assertEquals(0, fixture.requests.stream().filter(request -> !request.method().equals("GET")).count());
        }
      }
    }
  }

  @Test void aForbiddenResponseCannotHideChangedContentOrVersion() throws Exception {
    for (String mutation : java.util.List.of("file-content", "file-version", "event-content", "event-version")) {
      try (var fixture = new MutationFixture()) {
        fixture.changeDespiteDenial = mutation;
        ProductFlowException failure = assertThrows(ProductFlowException.class, fixture::verify);
        assertEquals("native denied mutation changed protected content or version", failure.getMessage());
      }
    }
  }

  @Test void unchangedEventContentDoesNotDependOnJsonPropertyOrder() throws Exception {
    try (var fixture = new MutationFixture()) {
      fixture.reorderEvent = true;
      fixture.verify();
    }
  }

  private record MutationRequest(String method, String path, String authorization, String version,
      String idempotencyKey, String contentType, String body) {}

  /** Unit HTTP fixture only; no real Weave, Flutter or release proof is produced here. */
  private final class MutationFixture implements AutoCloseable {
    private final com.sun.net.httpserver.HttpServer server;
    private final java.util.List<MutationRequest> requests = new java.util.concurrent.CopyOnWriteArrayList<>();
    private volatile String invalidCategory = "";
    private volatile int mutationStatus = 403;
    private volatile boolean invalidDenialCode;
    private volatile String unexpectedDenialCode = "invalid-payload";
    private volatile String fileVersion = "\"file-current\"";
    private volatile String eventVersion = "\"event-current\"";
    private volatile String changeDespiteDenial = "";
    private volatile boolean reorderEvent;
    private boolean writesObserved;

    MutationFixture() throws Exception {
      server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
      server.createContext("/", exchange -> {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        var headers = exchange.getRequestHeaders();
        requests.add(new MutationRequest(method, path, headers.getFirst("Authorization"), headers.getFirst("If-Match"),
            headers.getFirst("Idempotency-Key"), headers.getFirst("Content-Type"),
            new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)));
        String category = path.startsWith("/_matrix/") ? "chat" : path.startsWith("/api/files/") ? "files" : "calendar";
        int status;
        String body;
        if (!method.equals("GET")) {
          writesObserved = true;
          status = category.equals(invalidCategory) ? mutationStatus : 403;
          String code = category.equals(invalidCategory) && invalidDenialCode ? unexpectedDenialCode
              : category.equals("chat") ? "M_FORBIDDEN" : "capability-policy-blocked";
          body = JsonMapper.builder().build().writeValueAsString(
              Map.of(category.equals("chat") ? "errcode" : "code", code));
          exchange.getResponseHeaders().add("Content-Type", "application/json");
        } else {
          status = 200;
          String version = category.equals("files") ? fileVersion : eventVersion;
          if (writesObserved && changeDespiteDenial.equals(category.equals("files") ? "file-version" : "event-version")) {
            version = "\"changed\"";
          }
          if (!version.isEmpty()) exchange.getResponseHeaders().add("ETag", version);
          if (category.equals("files")) {
            body = writesObserved && changeDespiteDenial.equals("file-content") ? "changed" : "actual UI file bytes";
          } else if (writesObserved && changeDespiteDenial.equals("event-content")) {
            body = "{\"id\":\"event-owned\",\"title\":\"changed\"}";
          } else {
            body = writesObserved && reorderEvent
                ? "{\"title\":\"actual UI title\",\"id\":\"event-owned\"}"
                : "{\"id\":\"event-owned\",\"title\":\"actual UI title\"}";
          }
        }
        byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
      });
      server.start();
    }

    void verify() throws Exception {
      var mapper = JsonMapper.builder().build();
      var resources = mapper.valueToTree(ownedRequest().get("resources"));
      FreshProductFlow.requireNativeMutationDenials(new JsonHttpClient(java.net.http.HttpClient.newHttpClient()),
          java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()), resources, "owned-run",
          Map.of("Authorization", "Bearer denied-fixture"), Map.of("Authorization", "Bearer authorized-fixture"));
    }

    @Override public void close() { server.stop(0); }
  }

}
