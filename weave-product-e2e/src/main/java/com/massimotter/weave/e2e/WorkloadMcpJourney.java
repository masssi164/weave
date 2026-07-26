package com.massimotter.weave.e2e;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Per-cell private_key_jwt, client credentials, and MCP Streamable HTTP proof. */
final class WorkloadMcpJourney {
  private static final Set<String> MCP_SCOPES = Set.of("mcp:tools", "files.read");

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;

  WorkloadMcpJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  McpProof invokeFilesSearch(String cellRef, String expectedFileName) {
    String cellKey = requireCellKey(cellRef);
    String clientId = "weaver-cell-" + cellKey;
    RSAKey key = readActiveKey(clientId);
    String workloadToken = clientCredentials(clientId, key);
    validateWorkloadToken(workloadToken, clientId);

    ObjectNode initialize = request(1, "initialize");
    ObjectNode parameters = initialize.putObject("params");
    parameters.put("protocolVersion", "2025-11-25");
    parameters
        .putObject("capabilities")
        .putObject("extensions")
        .putObject("io.modelcontextprotocol/oauth-client-credentials");
    parameters.putObject("clientInfo").put("name", "weave-test-app").put("version", "1.0");

    JsonHttpClient.Response initialized =
        mcp(workloadToken, "", initialize, Set.of(200));
    String sessionId = initialized.firstHeader("Mcp-Session-Id");
    if (sessionId.isBlank()) {
      throw new ProductFlowException("MCP initialize omitted the session identifier");
    }
    JsonNode initializeResult = protocolBody(initialized);
    requireNoError(initializeResult, "MCP initialize");
    if (!initializeResult
        .path("result")
        .path("capabilities")
        .path("extensions")
        .path("io.modelcontextprotocol/oauth-client-credentials")
        .isObject()) {
      throw new ProductFlowException("MCP did not negotiate client credentials");
    }

    ObjectNode initializedNotification = request(null, "notifications/initialized");
    initializedNotification.set("params", http.mapper().createObjectNode());
    mcp(workloadToken, sessionId, initializedNotification, Set.of(200, 202, 204));

    ObjectNode list = request(2, "tools/list");
    list.set("params", http.mapper().createObjectNode());
    JsonNode tools = protocolBody(mcp(workloadToken, sessionId, list, Set.of(200)));
    requireNoError(tools, "MCP tools discovery");
    boolean found =
        stream(tools.path("result").path("tools"))
            .anyMatch(
                tool ->
                    "files.search".equals(tool.path("name").asString())
                        && tool.path("inputSchema").isObject());
    if (!found) {
      throw new ProductFlowException("MCP discovery omitted the files.search schema");
    }

    ObjectNode call = request(3, "tools/call");
    ObjectNode callParameters = call.putObject("params");
    callParameters.put("name", "files.search");
    callParameters
        .putObject("arguments")
        .put("query", expectedFileName)
        .put("path", "/")
        .put("limit", 10);
    JsonNode result = protocolBody(mcp(workloadToken, sessionId, call, Set.of(200)));
    requireNoError(result, "MCP files.search");
    String serialized = result.toString();
    if (!serialized.contains(expectedFileName)
        || !serialized.contains("weave://files/")
        || serialized.toLowerCase(java.util.Locale.ROOT).contains("nextcloud")
        || serialized.contains("/remote.php/dav")
        || serialized.contains("providerId")) {
      throw new ProductFlowException(
          "MCP files.search did not return a provider-neutral canonical match");
    }
    return new McpProof(clientId, "files.search", "weave-webdav", true);
  }

  private String clientCredentials(String clientId, RSAKey key) {
    URI tokenUri = environment.oidc("/protocol/openid-connect/token");
    Instant now = Instant.now();
    JWTClaimsSet claims =
        new JWTClaimsSet.Builder()
            .issuer(clientId)
            .subject(clientId)
            .audience(tokenUri.toString())
            .issueTime(Date.from(now))
            .expirationTime(Date.from(now.plusSeconds(45)))
            .jwtID(UUID.randomUUID().toString())
            .build();
    SignedJWT assertion =
        new SignedJWT(
            new JWSHeader.Builder(JWSAlgorithm.PS256)
                .type(JOSEObjectType.JWT)
                .keyID(key.getKeyID())
                .build(),
            claims);
    try {
      assertion.sign(new RSASSASigner(key));
    } catch (Exception failure) {
      throw new ProductFlowException("private_key_jwt signing failed", failure);
    }
    JsonNode token =
        http.form(
            "obtain MCP workload token",
            tokenUri,
            Map.of(
                "grant_type", "client_credentials",
                "client_id", clientId,
                "client_assertion_type",
                    "urn:ietf:params:oauth:client-assertion-type:jwt-bearer",
                "client_assertion", assertion.serialize(),
                "scope", "mcp:tools files.read"),
            Set.of(200));
    String accessToken = token.path("access_token").asString("").trim();
    if (accessToken.isEmpty()
        || token.path("refresh_token").isString()
        || token.path("id_token").isString()) {
      throw new ProductFlowException(
          "MCP workload token response violated the client-credentials contract");
    }
    return accessToken;
  }

  private void validateWorkloadToken(String token, String clientId) {
    JsonNode claims = jwtPayload(token);
    if (!environment.issuer().toString().equals(claims.path("iss").asString())
        || !clientId.equals(claims.path("client_id").asString())
        || !clientId.equals(claims.path("azp").asString())
        || claims.path("sub").asString("").isBlank()
        || claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
      throw new ProductFlowException("MCP workload token identity claims are invalid");
    }
    Set<String> audiences = strings(claims.path("aud"));
    Set<String> expectedAudiences =
        Set.of(environment.mcpEndpoint().toString(), "weave-mcp-server");
    if (!audiences.equals(expectedAudiences)) {
      throw new ProductFlowException("MCP workload token audience set is not exact");
    }
    Set<String> scopes =
        Set.of(claims.path("scope").asString("").trim().split("\\s+"));
    if (!scopes.equals(MCP_SCOPES)) {
      throw new ProductFlowException("MCP workload token scope set is not exact");
    }
    Set<String> roles = strings(claims.path("realm_access").path("roles"));
    if (!roles.equals(Set.of("weaver-runtime"))) {
      throw new ProductFlowException("MCP workload token role set is not exact");
    }
  }

  private RSAKey readActiveKey(String clientId) {
    Path root = environment.workloadCredentialRoot();
    Path path =
        root.resolve("weave")
            .resolve("agent-runtime")
            .resolve("cells")
            .resolve(clientId)
            .toAbsolutePath()
            .normalize();
    if (!path.startsWith(root)
        || Files.isSymbolicLink(path)
        || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
      throw new ProductFlowException("cell SecretRef material is unavailable");
    }
    requirePrivatePermissions(path);
    byte[] bytes;
    try {
      bytes = Files.readAllBytes(path);
    } catch (java.io.IOException failure) {
      throw new ProductFlowException("cell SecretRef material could not be read", failure);
    }
    try {
      if (bytes.length == 0 || bytes.length > 65_536) {
        throw new ProductFlowException("cell SecretRef material exceeded the safe bound");
      }
      JsonNode envelope = http.mapper().readTree(bytes);
      if (!clientId.equals(envelope.path("clientId").asString())
          || !"PRIVATE_KEY_JWT".equals(envelope.path("authenticationMethod").asString())) {
        throw new ProductFlowException("cell SecretRef envelope did not match the binding");
      }
      String activeKey = envelope.path("activeKeyId").asString();
      List<JsonNode> matches =
          stream(envelope.path("keys"))
              .filter(
                  candidate ->
                      activeKey.equals(candidate.path("keyId").asString())
                          && "ACTIVE".equals(candidate.path("status").asString()))
              .toList();
      if (matches.size() != 1) {
        throw new ProductFlowException("cell SecretRef has no unique active key");
      }
      try {
        RSAKey key = RSAKey.parse(matches.getFirst().path("privateJwk").toString());
        if (!key.isPrivate()
            || !JWSAlgorithm.PS256.equals(key.getAlgorithm())
            || !activeKey.equals(key.getKeyID())) {
          throw new ProductFlowException("cell SecretRef active key is invalid");
        }
        return key;
      } catch (java.text.ParseException failure) {
        throw new ProductFlowException("cell SecretRef private JWK is invalid", failure);
      }
    } catch (JacksonException failure) {
      throw new ProductFlowException("cell SecretRef envelope is invalid", failure);
    } finally {
      java.util.Arrays.fill(bytes, (byte) 0);
    }
  }

  private JsonHttpClient.Response mcp(
      String token,
      String sessionId,
      JsonNode request,
      Set<Integer> expectedStatuses) {
    byte[] body;
    try {
      body = http.mapper().writeValueAsBytes(request);
    } catch (JacksonException failure) {
      throw new ProductFlowException("MCP request encoding failed", failure);
    }
    Map<String, String> headers = new java.util.LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    headers.put("Accept", "application/json, text/event-stream");
    if (!sessionId.isBlank()) {
      headers.put("Mcp-Session-Id", sessionId);
    }
    return http.send(
        "invoke MCP Streamable HTTP",
        "POST",
        environment.mcpEndpoint(),
        Map.copyOf(headers),
        "application/json",
        body,
        expectedStatuses);
  }

  private JsonNode protocolBody(JsonHttpClient.Response response) {
    String body = response.bodyText().trim();
    if (body.startsWith("data:")) {
      body =
          body.lines()
              .filter(line -> line.startsWith("data:"))
              .map(line -> line.substring("data:".length()).trim())
              .filter(line -> !line.isBlank())
              .findFirst()
              .orElseThrow(
                  () -> new ProductFlowException("MCP event stream contained no JSON result"));
    }
    try {
      return http.mapper().readTree(body);
    } catch (RuntimeException failure) {
      throw new ProductFlowException("MCP returned an invalid protocol message", failure);
    }
  }

  private ObjectNode request(Integer id, String method) {
    ObjectNode request = http.mapper().createObjectNode();
    request.put("jsonrpc", "2.0");
    if (id != null) {
      request.put("id", id);
    }
    request.put("method", method);
    return request;
  }

  private static void requireNoError(JsonNode response, String operation) {
    if (!"2.0".equals(response.path("jsonrpc").asString())
        || response.path("error").isObject()
        || !response.path("result").isObject()) {
      throw new ProductFlowException(operation + " returned a JSON-RPC error");
    }
  }

  private JsonNode jwtPayload(String token) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new ProductFlowException("workload access token is not a JWT");
    }
    try {
      return http.mapper().readTree(Base64.getUrlDecoder().decode(parts[1]));
    } catch (RuntimeException failure) {
      throw new ProductFlowException("workload access token is invalid", failure);
    }
  }

  private static Set<String> strings(JsonNode node) {
    if (node.isString()) {
      return Set.of(node.asString());
    }
    if (!node.isArray()) {
      return Set.of();
    }
    Set<String> result = new HashSet<>();
    node.forEach(value -> result.add(value.asString()));
    return Set.copyOf(result);
  }

  private static java.util.stream.Stream<JsonNode> stream(JsonNode node) {
    if (!node.isArray()) {
      return java.util.stream.Stream.empty();
    }
    java.util.Spliterator<JsonNode> spliterator = node.spliterator();
    return java.util.stream.StreamSupport.stream(spliterator, false);
  }

  private static String requireCellKey(String cellRef) {
    if (cellRef == null || !cellRef.matches("cell:[A-Za-z0-9_-]{32}")) {
      throw new ProductFlowException("ARC returned an invalid cell reference");
    }
    return cellRef.substring("cell:".length());
  }

  private static void requirePrivatePermissions(Path path) {
    try {
      Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
      Set<PosixFilePermission> forbidden =
          EnumSet.of(
              PosixFilePermission.GROUP_READ,
              PosixFilePermission.GROUP_WRITE,
              PosixFilePermission.GROUP_EXECUTE,
              PosixFilePermission.OTHERS_READ,
              PosixFilePermission.OTHERS_WRITE,
              PosixFilePermission.OTHERS_EXECUTE);
      if (!java.util.Collections.disjoint(permissions, forbidden)) {
        throw new ProductFlowException("cell SecretRef permissions are too broad");
      }
    } catch (UnsupportedOperationException ignored) {
      // Regular-file and no-symlink checks remain binding on non-POSIX systems.
    } catch (java.io.IOException failure) {
      throw new ProductFlowException("cell SecretRef permissions are unavailable", failure);
    }
  }

  record McpProof(
      String clientId, String toolName, String serverProjection, boolean canonicalResourceSeen) {}
}
