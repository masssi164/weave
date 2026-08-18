package com.massimotter.weave.mcp;

import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weave.mcp")
public record McpWorkloadProperties(
    URI resourceUri,
    URI resourceMetadataUri,
    URI authorizationServer,
    List<String> requiredScopes,
    URI tokenUri,
    String exchangeClientId,
    Path exchangeClientJwkFile,
    URI backendResourceUri,
    URI backendFilesUri,
    List<String> exchangeScopes,
    Duration requestTimeout,
    Duration maximumTokenTtl,
    int maximumRequestBytes) {

  public static final String CLIENT_CREDENTIALS_EXTENSION =
      "io.modelcontextprotocol/oauth-client-credentials";

  public McpWorkloadProperties {
    resourceUri = https(resourceUri, "resourceUri");
    resourceMetadataUri = https(resourceMetadataUri, "resourceMetadataUri");
    authorizationServer = https(authorizationServer, "authorizationServer");
    requiredScopes = exactScopes(requiredScopes, "requiredScopes");
    tokenUri = http(tokenUri, "tokenUri");
    if (!"weave-mcp-server".equals(exchangeClientId)) {
      throw new IllegalArgumentException("exchangeClientId must be weave-mcp-server");
    }
    if (exchangeClientJwkFile == null || !exchangeClientJwkFile.isAbsolute()) {
      throw new IllegalArgumentException(
          "exchangeClientJwkFile must be an absolute SecretRef path");
    }
    backendResourceUri = https(backendResourceUri, "backendResourceUri");
    backendFilesUri = http(backendFilesUri, "backendFilesUri");
    if (!backendFilesUri.getPath().endsWith("/dav/files")) {
      throw new IllegalArgumentException(
          "backendFilesUri must target the Weave Files WebDAV facade");
    }
    exchangeScopes = exactScopes(exchangeScopes, "exchangeScopes");
    if (!requiredScopes.containsAll(exchangeScopes)
        || exchangeScopes.contains("mcp.tools")
        || exchangeScopes.contains("agent-runtime.profile.read")) {
      throw new IllegalArgumentException(
          "exchangeScopes must be a domain-only subset of requiredScopes");
    }
    requestTimeout = requestTimeout == null ? Duration.ofSeconds(10) : requestTimeout;
    maximumTokenTtl = maximumTokenTtl == null ? Duration.ofSeconds(60) : maximumTokenTtl;
    if (requestTimeout.isZero()
        || requestTimeout.isNegative()
        || requestTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
      throw new IllegalArgumentException("requestTimeout must be between zero and 30 seconds");
    }
    if (maximumTokenTtl.compareTo(Duration.ofSeconds(5)) < 0
        || maximumTokenTtl.compareTo(Duration.ofMinutes(5)) > 0) {
      throw new IllegalArgumentException("maximumTokenTtl must be between 5 seconds and 5 minutes");
    }
    maximumRequestBytes = maximumRequestBytes == 0 ? 1_048_576 : maximumRequestBytes;
    if (maximumRequestBytes < 1024 || maximumRequestBytes > 4_194_304) {
      throw new IllegalArgumentException("maximumRequestBytes is outside the safe bound");
    }
  }

  private static URI https(URI value, String field) {
    URI result = http(value, field);
    if (!"https".equalsIgnoreCase(result.getScheme())) {
      throw new IllegalArgumentException(field + " must use HTTPS");
    }
    return result;
  }

  private static URI http(URI value, String field) {
    if (value == null
        || value.getHost() == null
        || value.getUserInfo() != null
        || value.getQuery() != null
        || value.getFragment() != null
        || !("https".equalsIgnoreCase(value.getScheme())
            || "http".equalsIgnoreCase(value.getScheme()))) {
      throw new IllegalArgumentException(field + " must be an absolute HTTP(S) URI");
    }
    return value;
  }

  private static List<String> exactScopes(List<String> values, String field) {
    List<String> copy =
        values == null
            ? List.of()
            : values.stream().map(value -> value == null ? "" : value.trim()).toList();
    if (copy.isEmpty()
        || copy.stream().anyMatch(String::isBlank)
        || new LinkedHashSet<>(copy).size() != copy.size()) {
      throw new IllegalArgumentException(field + " must be non-empty and unique");
    }
    return List.copyOf(copy);
  }
}
