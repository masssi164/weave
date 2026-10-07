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
import java.net.URLDecoder;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Per-cell private_key_jwt, client credentials, and MCP Streamable HTTP proof. */
final class WorkloadMcpJourney {
  private static final long MAXIMUM_WORKLOAD_TOKEN_TTL_SECONDS = 60;
  private static final Set<String> FILES_SCOPES = Set.of("mcp.tools", "files.read");
  private static final Set<String> CALENDAR_SCOPES = Set.of("mcp.tools", "calendar.read");
  private static final Set<String> CALENDAR_WRITE_SCOPES = Set.of("mcp.tools", "calendar.write");
  private static final Pattern FILES_ERROR_CODE =
      Pattern.compile("Files User API rejected request: HTTP ([0-9]{3})");
  private static final Pattern CALENDAR_ERROR_CODE =
      Pattern.compile("Calendar User API rejected request: HTTP ([0-9]{3})");
  private static final Pattern FILES_RESOURCE_URI =
      Pattern.compile("weave://files/[A-Za-z0-9%:_-]+");

  private final ProductFlowEnvironment environment;
  private final JsonHttpClient http;

  WorkloadMcpJourney(ProductFlowEnvironment environment, JsonHttpClient http) {
    this.environment = environment;
    this.http = http;
  }

  McpProof invokeFilesSearch(String cellRef, GeneratedFilesJourney.Proof proof) {
    String cellKey = requireCellKey(cellRef);
    String clientId = "weaver-cell-" + cellKey;
    RSAKey key = readActiveKey(clientId);
    String workloadToken = clientCredentials(clientId, key, FILES_SCOPES);
    validateWorkloadToken(workloadToken, clientId, FILES_SCOPES);

    String sessionId = initializeSession(workloadToken);
    requireTool(workloadToken, sessionId, "files.search");

    ObjectNode call = request(3, "tools/call");
    ObjectNode callParameters = call.putObject("params");
    callParameters.put("name", "files.search");
    callParameters
        .putObject("arguments")
        .put("query", proof.name())
        .put("path", "/")
        .put("limit", 10);
    JsonNode result = protocolBody(mcp(workloadToken, sessionId, call, Set.of(200)));
    requireNoError(result, "MCP files.search");
    String serialized = result.toString();
    if (!serialized.contains(proof.name())
        || !serialized.contains("weave://files/")
        || serialized.toLowerCase(java.util.Locale.ROOT).contains("nextcloud")
        || serialized.contains("/remote.php/dav")
        || serialized.contains("providerId")) {
      throw new ProductFlowException(
          "MCP files.search did not return a provider-neutral canonical match");
    }
    Matcher resource = FILES_RESOURCE_URI.matcher(serialized);
    String resourceUri = "";
    while (resource.find()) {
      String candidate = resource.group();
      String candidateId = URLDecoder.decode(
          candidate.substring("weave://files/".length()), StandardCharsets.UTF_8);
      if (proof.fileId().equals(candidateId)) {
        resourceUri = candidate;
        break;
      }
    }
    if (resourceUri.isBlank()) {
      throw new ProductFlowException("MCP files.search omitted the expected stable file reference");
    }
    ObjectNode read = request(4, "resources/read");
    read.putObject("params").put("uri", resourceUri);
    JsonNode readResult = protocolBody(mcp(workloadToken, sessionId, read, Set.of(200)));
    requireNoError(readResult, "MCP Files resource read");
    String expectedContent = new String(proof.content(), StandardCharsets.UTF_8);
    if (stream(readResult.path("result").path("contents"))
            .noneMatch(content -> expectedContent.equals(content.path("text").asString()))
        || readResult.toString().toLowerCase(java.util.Locale.ROOT).contains("nextcloud")
        || readResult.toString().contains("/remote.php/dav")) {
      throw new ProductFlowException("MCP Files resource read changed the generated User content");
    }
    return new McpProof(clientId, "files.search", "weave-user-api", true);
  }

  void invokeCalendarAgenda(String cellRef, GeneratedCalendarJourney.Proof proof) {
    String clientId = "weaver-cell-" + requireCellKey(cellRef);
    RSAKey key = readActiveKey(clientId);
    ObjectNode call = request(3, "tools/call");
    call.putObject("params")
        .put("name", "calendar.agenda")
        .putObject("arguments")
        .put("calendarId", proof.calendarId())
        .put("from", "2026-10-23T00:00:00Z")
        .put("to", "2026-10-29T00:00:00Z")
        .put("evaluationTimeZone", "Europe/Berlin");

    String filesToken = clientCredentials(clientId, key, FILES_SCOPES);
    validateWorkloadToken(filesToken, clientId, FILES_SCOPES);
    String filesSession = initializeSession(filesToken);
    JsonHttpClient.Response wrongScope = mcp(filesToken, filesSession, call, Set.of(403));
    if (!wrongScope.firstHeader("WWW-Authenticate").contains("error=\"insufficient_scope\"")
        || !wrongScope.bodyText().contains("\"error\":\"insufficient_scope\"")) {
      throw new ProductFlowException("a Files-only Cell token reached Calendar MCP");
    }

    String token = clientCredentials(clientId, key, CALENDAR_SCOPES);
    validateWorkloadToken(token, clientId, CALENDAR_SCOPES);
    String sessionId = initializeSession(token);
    requireTool(token, sessionId, "calendar.agenda");
    JsonNode result = protocolBody(mcp(token, sessionId, call, Set.of(200)));
    requireNoError(result, "MCP calendar.agenda");
    String serialized = result.path("result").toString();
    for (GeneratedCalendarJourney.EventProof expected : proof.events()) {
      if (!serialized.contains(expected.event().getId())
          || !serialized.contains(expected.event().getContent().getTitle())) {
        throw new ProductFlowException("MCP Calendar omitted an authorized persisted event");
      }
    }
    if (!serialized.contains(proof.calendarId())
        || serialized.contains("providerId")
        || serialized.toLowerCase(java.util.Locale.ROOT).contains("nextcloud")) {
      throw new ProductFlowException("MCP Calendar changed the provider-neutral agenda projection");
    }
  }

  void verifyCalendarWriteDeniedForMember(String cellRef, String calendarId) {
    String clientId = "weaver-cell-" + requireCellKey(cellRef);
    RSAKey key = readActiveKey(clientId);
    ObjectNode call = request(3, "tools/call");
    ObjectNode arguments = call.putObject("params")
        .put("name", "calendar.create")
        .putObject("arguments");
    arguments.put("calendarId", calendarId)
        .put("idempotencyKey", "calendar-mcp-member-denied-" + environment.runId());
    ObjectNode event = arguments.putObject("event");
    event.put("title", "Denied MCP Calendar write " + environment.runId());
    event.putObject("start").put("kind", "DATE").put("date", "2026-10-25");
    event.putObject("end").put("kind", "DATE").put("date", "2026-10-26");
    event.putArray("attendees");
    event.putArray("overrides");

    String readToken = clientCredentials(clientId, key, CALENDAR_SCOPES);
    validateWorkloadToken(readToken, clientId, CALENDAR_SCOPES);
    String readSession = initializeSession(readToken);
    JsonHttpClient.Response wrongScope = mcp(readToken, readSession, call, Set.of(403));
    if (!wrongScope.firstHeader("WWW-Authenticate").contains("error=\"insufficient_scope\"")) {
      throw new ProductFlowException("a Calendar read-only token reached a Calendar mutation");
    }

    String writeToken = clientCredentials(clientId, key, CALENDAR_WRITE_SCOPES);
    validateWorkloadToken(writeToken, clientId, CALENDAR_WRITE_SCOPES);
    String writeSession = initializeSession(writeToken);
    requireTool(writeToken, writeSession, "calendar.create");
    JsonNode denied = protocolBody(mcp(writeToken, writeSession, call, Set.of(200)));
    String result = denied.path("result").toString();
    if (!denied.path("result").path("isError").asBoolean(false)
        || !result.contains("Calendar User API rejected request: HTTP 403")
        || result.contains("providerId") || result.contains("access_token")) {
      throw new ProductFlowException("a member without calendar.manage_events could write over MCP");
    }
  }

  private String initializeSession(String workloadToken) {

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

    return sessionId;
  }

  private void requireTool(String workloadToken, String sessionId, String toolName) {
    ObjectNode list = request(2, "tools/list");
    list.set("params", http.mapper().createObjectNode());
    JsonNode tools = protocolBody(mcp(workloadToken, sessionId, list, Set.of(200)));
    requireNoError(tools, "MCP tools discovery");
    boolean found =
        stream(tools.path("result").path("tools"))
            .anyMatch(
                tool ->
                    toolName.equals(tool.path("name").asString())
                        && tool.path("inputSchema").isObject());
    if (!found) {
      throw new ProductFlowException("MCP discovery omitted the " + toolName + " schema");
    }
  }

  private String clientCredentials(String clientId, RSAKey key, Set<String> scopes) {
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
                "scope", String.join(" ", scopes.stream().sorted().toList())),
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

  private void validateWorkloadToken(String token, String clientId, Set<String> expectedScopes) {
    JsonNode header = jwtPart(token, 0);
    if (!hasRfc9068TokenType(header)) {
      throw new ProductFlowException("MCP workload token type is not RFC 9068 at+jwt");
    }
    JsonNode claims = jwtPayload(token);
    Set<String> invalidIdentityClaims =
        invalidIdentityClaims(
            claims, clientId, environment.issuer().toString(), Instant.now().getEpochSecond());
    if (!invalidIdentityClaims.isEmpty()) {
      throw new ProductFlowException(
          "MCP workload token identity claims are invalid fields="
              + String.join(",", invalidIdentityClaims));
    }
    Set<String> audiences = strings(claims.path("aud"));
    Set<String> expectedAudiences =
        Set.of(environment.mcpEndpoint().toString(), "weave-mcp-server");
    if (!audiences.equals(expectedAudiences)) {
      throw new ProductFlowException("MCP workload token audience set is not exact");
    }
    Set<String> scopes =
        Set.of(claims.path("scope").asString("").trim().split("\\s+"));
    if (!scopes.equals(expectedScopes)) {
      throw new ProductFlowException("MCP workload token scope set is not exact");
    }
    Set<String> roles = strings(claims.path("realm_access").path("roles"));
    if (!roles.equals(Set.of("weaver-runtime"))) {
      throw new ProductFlowException("MCP workload token role set is not exact");
    }
  }

  static Set<String> invalidIdentityClaims(
      JsonNode claims, String clientId, String issuer, long currentEpochSecond) {
    Set<String> invalid = new java.util.TreeSet<>();
    if (!issuer.equals(claims.path("iss").asString())) {
      invalid.add("issuer");
    }
    if (!clientId.equals(claims.path("client_id").asString())) {
      invalid.add("client-id");
    }
    if (!clientId.equals(claims.path("azp").asString())) {
      invalid.add("authorized-party");
    }
    if (claims.path("sub").asString("").isBlank()) {
      invalid.add("subject");
    }
    if (claims.path("jti").asString("").isBlank()) {
      invalid.add("token-id");
    }
    long issuedAt = claims.path("iat").asLong(0);
    if (issuedAt <= 0) {
      invalid.add("issued-at");
    }
    if (claims.path("exp").asLong(0) <= currentEpochSecond) {
      invalid.add("expiry");
    }
    if (issuedAt > 0
        && claims.path("exp").asLong(0) - issuedAt > MAXIMUM_WORKLOAD_TOKEN_TTL_SECONDS) {
      invalid.add("token-ttl");
    }
    return Set.copyOf(invalid);
  }

  static boolean hasRfc9068TokenType(JsonNode header) {
    return "at+jwt".equalsIgnoreCase(header.path("typ").asString());
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
    String method = request.path("method").asString("");
    String operation = switch (method) {
      case "initialize" -> "initialize";
      case "tools/list" -> "tools/list";
      case "tools/call" -> {
        String name = request.path("params").path("name").asString("");
        yield "tools/call " + (name.equals("files.search") || name.equals("calendar.agenda")
            || name.equals("calendar.create") || name.equals("calendar.update")
            || name.equals("calendar.delete") ? name : "unknown");
      }
      default -> "unknown method";
    };
    return http.send(
        "invoke MCP Streamable HTTP " + operation,
        "POST",
        environment.mcpEndpoint(),
        Map.copyOf(headers),
        "application/json",
        body,
        expectedStatuses);
  }

  private JsonNode protocolBody(JsonHttpClient.Response response) {
    String body = protocolPayload(response.bodyText());
    try {
      return http.mapper().readTree(body);
    } catch (RuntimeException failure) {
      throw new ProductFlowException("MCP returned an invalid protocol message", failure);
    }
  }

  static String protocolPayload(String responseBody) {
    String body = responseBody == null ? "" : responseBody.trim();
    String eventData =
        body.lines()
            .map(String::trim)
            .filter(line -> line.startsWith("data:"))
            .map(line -> line.substring("data:".length()).trim())
            .filter(line -> !line.isBlank())
            .findFirst()
            .orElse("");
    return eventData.isBlank() ? body : eventData;
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
        || !response.path("result").isObject()
        || response.path("result").path("isError").asBoolean(false)) {
      throw new ProductFlowException(
          operation + " returned a JSON-RPC error class=" + supportSafeErrorClass(response));
    }
  }

  static String supportSafeErrorClass(JsonNode response) {
    Matcher filesError = FILES_ERROR_CODE.matcher(response.toString());
    if (filesError.find()) {
      return "files-user-http-" + filesError.group(1);
    }
    Matcher calendarError = CALENDAR_ERROR_CODE.matcher(response.toString());
    if (calendarError.find()) {
      return "calendar-user-http-" + calendarError.group(1);
    }
    String toolContent = response.path("result").path("content").path(0).path("text").asString("");
    if (toolContent.contains("The Calendar is unavailable to the current member")) {
      return "calendar-not-visible";
    }
    if (toolContent.contains("The Calendar agenda exceeds the MCP result bound")
        || toolContent.contains("The Calendar agenda exceeds the MCP byte bound")) {
      return "calendar-result-bound";
    }
    if (toolContent.contains("A stable Weave Calendar reference is required")
        || toolContent.contains("A valid bounded Calendar interval and time zone are required")
        || toolContent.contains("The Calendar interval must be at most 366 days")) {
      return "calendar-invalid-arguments";
    }
    JsonNode code = response.path("error").path("code");
    if (code.canConvertToInt()) {
      return "json-rpc-" + code.intValue();
    }
    return "redacted-tool-error";
  }

  private JsonNode jwtPayload(String token) {
    return jwtPart(token, 1);
  }

  private JsonNode jwtPart(String token, int index) {
    String[] parts = token.split("\\.");
    if (parts.length != 3) {
      throw new ProductFlowException("workload access token is not a JWT");
    }
    try {
      return http.mapper().readTree(Base64.getUrlDecoder().decode(parts[index]));
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
