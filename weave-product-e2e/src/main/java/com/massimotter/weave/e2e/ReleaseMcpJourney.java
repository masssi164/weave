package com.massimotter.weave.e2e;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
import com.massimotter.weave.userapi.model.CalendarUserEvent;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Disposable, revocable release MCP binding; no ARC Cell or signed profile is created. */
final class ReleaseMcpJourney {
  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;
  private final WorkloadMcpJourney mcp;
  private final String clientId;
  private final RSAKey key;
  private final String workloadSubject;
  private final Path bindingFile;
  private final String bindingRef;

  ReleaseMcpJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
    this.mcp = new WorkloadMcpJourney(environment, http);
    Path root = environment.workloadCredentialRoot();
    Path clientFile = root.resolve("release-mcp-client.json");
    this.bindingFile = root.resolve("release-mcp.json");
    this.bindingRef = "mcp-binding:" + Hashing.sha256(environment.runId()).substring(0, 24);
    if (Files.isSymbolicLink(clientFile)
        || !Files.isRegularFile(clientFile, LinkOption.NOFOLLOW_LINKS)) {
      throw new ProductFlowException("release MCP workload SecretRef is unavailable");
    }
    try {
      JsonNode source = http.mapper().readTree(Files.readAllBytes(clientFile));
      clientId = source.path("clientId").asString("");
      if (!clientId.matches("weaver-cell-[0-9a-f]{16}")) {
        throw new ProductFlowException("release MCP workload client is invalid");
      }
      key = RSAKey.parse(source.path("privateJwk").toString());
      if (!key.isPrivate() || !JWSAlgorithm.PS256.equals(key.getAlgorithm())) {
        throw new ProductFlowException("release MCP workload key is invalid");
      }
    } catch (IOException | java.text.ParseException failure) {
      throw new ProductFlowException("release MCP workload SecretRef cannot be read", failure);
    }
    String token = mcp.clientCredentials(clientId, key, Set.of("mcp.tools", "files.read"));
    workloadSubject = mcp.jwtPayload(token).path("sub").asString("");
    if (workloadSubject.isBlank()) {
      throw new ProductFlowException("release MCP workload has no stable subject");
    }
  }

  String bindingRef() {
    return bindingRef;
  }

  void bind(String memberSubject, String personRef, boolean active) {
    ObjectNode root = http.mapper().createObjectNode();
    root.put("schemaVersion", 1);
    ObjectNode entry = root.putArray("bindings").addObject();
    entry.put("bindingRef", bindingRef);
    entry.put("workloadIssuer", environment.issuer().toString());
    entry.put("workloadSubject", workloadSubject);
    entry.put("workloadClientId", clientId);
    entry.put("organizationRef", environment.tenantId());
    entry.put("personRef", personRef);
    entry.put("memberIssuer", environment.issuer().toString());
    entry.put("memberSubject", memberSubject);
    entry.put("expiresAt", Instant.now().plusSeconds(3600).toString());
    entry.putArray("allowedToolClasses")
        .add("files.read").add("calendar.read").add("calendar.write");
    entry.put("active", active);
    try {
      Path temporary = Files.createTempFile(bindingFile.getParent(), ".release-mcp-", ".tmp",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      try {
        Files.write(temporary, http.mapper().writeValueAsBytes(root));
        Files.move(temporary, bindingFile,
            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } finally {
        Files.deleteIfExists(temporary);
      }
    } catch (IOException | JacksonException failure) {
      throw new ProductFlowException("release MCP binding could not be atomically updated", failure);
    }
  }

  WorkloadMcpJourney.McpProof files(GeneratedFilesJourney.Proof proof) {
    return mcp.invokeFilesSearch(clientId, key, proof);
  }

  void calendar(GeneratedCalendarJourney.Proof proof) {
    mcp.invokeCalendarAgenda(clientId, key, proof);
  }

  void verifyCalendarWriteDenied(String calendarId) {
    mcp.verifyCalendarWriteDeniedForMember(clientId, key, calendarId);
  }

  void verifyCalendarWriteParity(
      GeneratedCalendarJourney calendar, String calendarId, String authorToken) {
    mcp.verifyCalendarWriteParity(
        clientId, key, calendar, calendarId, authorToken, environment.runId());
  }

  void proveOpenClawCalendarWriteDenied(String calendarId) {
    String title = "Denied OpenClaw Calendar " + Hashing.sha256(environment.runId()).substring(0, 16);
    ObjectNode expected = openClawCalendarWrite("calendar.create", calendarId);
    ObjectNode arguments = (ObjectNode) expected.path("arguments");
    arguments.put("idempotencyKey", "openclaw-denied-" + Hashing.sha256(environment.runId()));
    arguments.set("event", calendarWriteEvent(title));
    expected.putArray("contains").add("Calendar User API rejected request: HTTP 403");
    proveOpenClaw(expected, Set.of("mcp.tools", "calendar.write"));
  }

  String proveOpenClawCalendarWriteParity(
      GeneratedCalendarJourney calendar, String calendarId, String authorToken) {
    Set<String> scopes = Set.of("mcp.tools", "calendar.write");
    String title = "OpenClaw Calendar " + Hashing.sha256(environment.runId()).substring(0, 16);
    String idempotencyKey = "openclaw-calendar-" + Hashing.sha256(environment.runId());
    ObjectNode create = openClawCalendarWrite("calendar.create", calendarId);
    ObjectNode createArguments = (ObjectNode) create.path("arguments");
    createArguments.put("idempotencyKey", idempotencyKey);
    createArguments.set("event", calendarWriteEvent(title));
    create.putArray("contains").add(title);
    String version = proveOpenClaw(create, scopes);
    CalendarUserEvent created = calendar.readMcpEvent(calendarId, title, authorToken);
    calendar.verifyMcpEventUnchanged(calendarId, created, authorToken);

    if (!version.equals(proveOpenClaw(create, scopes))) {
      throw new ProductFlowException("OpenClaw version changed across Calendar create replay");
    }
    calendar.verifyMcpEventUnchanged(calendarId, created, authorToken);

    String updatedTitle = title + " updated";
    ObjectNode update = openClawCalendarWrite("calendar.update", calendarId);
    ObjectNode updateArguments = (ObjectNode) update.path("arguments");
    updateArguments.put("eventId", created.getId());
    updateArguments.put("ifMatch", created.getVersion());
    updateArguments.set("event", calendarWriteEvent(updatedTitle));
    update.putArray("contains").add(created.getId()).add(updatedTitle);
    if (!version.equals(proveOpenClaw(update, scopes))) {
      throw new ProductFlowException("OpenClaw version changed across Calendar update");
    }
    CalendarUserEvent updated = calendar.readMcpEvent(calendarId, updatedTitle, authorToken);
    if (!created.getId().equals(updated.getId())
        || created.getVersion().equals(updated.getVersion())) {
      throw new ProductFlowException("OpenClaw Calendar update lost identity or version advance");
    }

    ObjectNode stale = openClawCalendarWrite("calendar.update", calendarId);
    ObjectNode staleArguments = (ObjectNode) stale.path("arguments");
    staleArguments.put("eventId", created.getId());
    staleArguments.put("ifMatch", created.getVersion());
    staleArguments.set("event", calendarWriteEvent(title + " stale"));
    stale.putArray("contains").add("Calendar User API rejected request: HTTP 412");
    if (!version.equals(proveOpenClaw(stale, scopes))) {
      throw new ProductFlowException("OpenClaw version changed across Calendar stale update");
    }
    calendar.verifyMcpEventUnchanged(calendarId, updated, authorToken);

    ObjectNode delete = openClawCalendarWrite("calendar.delete", calendarId);
    ObjectNode deleteArguments = (ObjectNode) delete.path("arguments");
    deleteArguments.put("eventId", updated.getId());
    deleteArguments.put("ifMatch", updated.getVersion());
    delete.putArray("contains").add(updated.getId()).add("deleted");
    if (!version.equals(proveOpenClaw(delete, scopes))) {
      throw new ProductFlowException("OpenClaw version changed across Calendar delete");
    }
    calendar.verifyMcpEventDeleted(calendarId, updated.getId(), authorToken);
    return version;
  }

  private ObjectNode openClawCalendarWrite(String tool, String calendarId) {
    ObjectNode expected = http.mapper().createObjectNode();
    expected.put("tool", tool);
    expected.putObject("arguments").put("calendarId", calendarId);
    return expected;
  }

  private ObjectNode calendarWriteEvent(String title) {
    ObjectNode event = http.mapper().createObjectNode();
    event.put("title", title);
    event.putObject("start").put("kind", "DATE").put("date", "2026-10-25");
    event.putObject("end").put("kind", "DATE").put("date", "2026-10-26");
    event.putArray("attendees");
    event.putArray("overrides");
    return event;
  }

  String proveOpenClawFiles(GeneratedFilesJourney.Proof proof) {
    ObjectNode expected = http.mapper().createObjectNode();
    expected.put("tool", "files.search");
    expected.putObject("arguments")
        .put("query", proof.name()).put("path", "/").put("limit", 10);
    expected.putArray("contains").add(proof.name()).add(proof.fileId());
    return proveOpenClaw(expected, Set.of("mcp.tools", "files.read"));
  }

  String proveOpenClawCalendar(GeneratedCalendarJourney.Proof proof) {
    ObjectNode expected = http.mapper().createObjectNode();
    expected.put("tool", "calendar.agenda");
    expected.putObject("arguments")
        .put("calendarId", proof.calendarId())
        .put("from", "2026-10-23T00:00:00Z")
        .put("to", "2026-10-29T00:00:00Z")
        .put("evaluationTimeZone", "Europe/Berlin");
    var contents = expected.putArray("contains").add(proof.calendarId());
    for (GeneratedCalendarJourney.EventProof event : proof.events()) {
      contents.add(event.event().getId()).add(event.event().getContent().getTitle());
    }
    return proveOpenClaw(expected, Set.of("mcp.tools", "calendar.read"));
  }

  private String proveOpenClaw(ObjectNode expected, Set<String> scopes) {
    String port = System.getProperty("weave.e2e.mcp-local-port", "");
    String script = System.getProperty("weave.e2e.openclaw-script", "");
    if (!port.matches("[0-9]{4,5}") || Integer.parseInt(port) > 65_535
        || !Path.of(script).isAbsolute() || !Files.isRegularFile(Path.of(script))) {
      throw new ProductFlowException("real OpenClaw proof inputs are unavailable");
    }
    Path expectedFile;
    try {
      expectedFile = Files.createTempFile(
          environment.evidenceFile().getParent(), ".openclaw-expected-", ".json",
          PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
      Files.write(expectedFile, http.mapper().writeValueAsBytes(expected));
    } catch (IOException | JacksonException failure) {
      throw new ProductFlowException("independent OpenClaw expectation cannot be prepared", failure);
    }
    try {
      ArrayList<String> command = new ArrayList<>();
      command.add("python3");
      command.add(script);
      command.add("--mcp-url");
      command.add("http://127.0.0.1:" + port + "/mcp");
      command.add("--expected");
      command.add(expectedFile.toString());
      command.add("--private-root");
      command.add(environment.evidenceFile().getParent().toString());
      ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
      builder.environment().put("WEAVE_MCP_WORKLOAD_TOKEN",
          mcp.clientCredentials(clientId, key, scopes));
      Process process = builder.start();
      if (!process.waitFor(Duration.ofSeconds(120).toSeconds(), TimeUnit.SECONDS)) {
        process.destroyForcibly();
        throw new ProductFlowException("real OpenClaw MCP invocation exceeded its deadline");
      }
      String output = new String(process.getInputStream().readNBytes(4096));
      if (process.exitValue() != 0
          || !output.contains("WEAVE_OPENCLAW_RELEASE_MCP_RESULT status=passed")) {
        throw new ProductFlowException("real OpenClaw MCP invocation failed");
      }
      java.util.regex.Matcher version = java.util.regex.Pattern
          .compile("clientVersion=(20[0-9]{2}\\.[0-9]+\\.[0-9]+)")
          .matcher(output);
      if (!version.find()) {
        throw new ProductFlowException("real OpenClaw MCP invocation omitted its version");
      }
      return version.group(1);
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      throw new ProductFlowException("real OpenClaw MCP invocation was interrupted", failure);
    } catch (IOException failure) {
      throw new ProductFlowException("real OpenClaw MCP invocation could not start", failure);
    } finally {
      try {
        Files.deleteIfExists(expectedFile);
      } catch (IOException failure) {
        throw new ProductFlowException("OpenClaw expectation cleanup failed", failure);
      }
    }
  }
}
