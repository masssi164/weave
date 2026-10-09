package com.massimotter.weave.e2e;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.RSAKey;
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
import java.util.Set;

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
}
