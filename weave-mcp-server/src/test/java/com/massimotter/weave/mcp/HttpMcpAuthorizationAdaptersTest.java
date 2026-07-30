package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class HttpMcpAuthorizationAdaptersTest {
  private static final String ISSUER = "https://auth.weave.test/realms/weave";
  private static final String MCP_RESOURCE = "https://api.weave.test/mcp";
  private static final String API_RESOURCE = "https://api.weave.test/api";
  private static final String EDGE = "weave-mcp-server";
  private static final String CLIENT = "weaver-cell-test";
  private static final String SUBJECT = "service-account-cell-subject";

  @TempDir Path temporary;

  private HttpServer server;
  private JsonMapper mapper;
  private Path jwkFile;
  private Instant now;

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    mapper = JsonMapper.builder().build();
    jwkFile = temporary.resolve("mcp-edge.jwk");
    Files.writeString(
        jwkFile,
        new RSAKeyGenerator(2048).keyID("mcp-edge-test").generate().toJSONString(),
        StandardCharsets.UTF_8);
    try {
      Files.setPosixFilePermissions(jwkFile, PosixFilePermissions.fromString("rw-------"));
    } catch (UnsupportedOperationException ignored) {
      // Production retains regular-file, no-symlink, and readability checks.
    }
    now = Instant.now().minusSeconds(1);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void springSecurityExchangesWithPrivateKeyJwtAndNeverRelaysInboundBearer() {
    AtomicReference<String> authorization = new AtomicReference<>();
    AtomicReference<Map<String, String>> form = new AtomicReference<>();
    server.createContext(
        "/token",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          form.set(
              form(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
          byte[] response =
              mapper.writeValueAsBytes(
                  Map.of(
                      "access_token", "opaque.backend.token",
                      "issued_token_type", "urn:ietf:params:oauth:token-type:access_token",
                      "token_type", "Bearer",
                      "scope", "files.read",
                      "expires_in", 30));
          exchange.getResponseHeaders().set("Content-Type", "application/json");
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();

    ExchangedAccessToken result =
        tokenExchange(properties("/token"))
            .exchange(workload(), "incoming.cell.token", Set.of("files.read"));

    assertThat(authorization.get()).isNull();
    assertThat(form.get())
        .containsEntry("client_id", EDGE)
        .containsEntry("grant_type", "urn:ietf:params:oauth:grant-type:token-exchange")
        .containsEntry("subject_token", "incoming.cell.token")
        .containsEntry("subject_token_type", "urn:ietf:params:oauth:token-type:access_token")
        .containsEntry("scope", "files.read")
        .containsKey("client_assertion")
        .containsEntry(
            "client_assertion_type", "urn:ietf:params:oauth:client-assertion-type:jwt-bearer")
        .doesNotContainKeys(
            "audience", "resource", "client_secret", "actor_token", "requested_subject");
    assertThat(result.value()).isEqualTo("opaque.backend.token");
    assertThat(result.scopes()).containsExactly("files.read");
    assertThat(result.toString()).isEqualTo("ExchangedAccessToken[redacted]");
  }

  @Test
  void rejectsScopeEscalationBeforeCallingTheAuthorizationServer() {
    assertThatThrownBy(
            () ->
                tokenExchange(properties("/token"))
                    .exchange(workload(), "incoming.cell.token", Set.of("files.write")))
        .isInstanceOfSatisfying(
            McpAdmissionException.class,
            failure -> assertThat(failure.kind()).isEqualTo(McpAdmissionException.Kind.FORBIDDEN));
  }

  private SpringSecurityMcpBackendTokenExchange tokenExchange(
      McpWorkloadProperties properties) {
    McpAuthorizationConfiguration configuration = new McpAuthorizationConfiguration();
    return new SpringSecurityMcpBackendTokenExchange(
        properties,
        configuration.mcpBackendExchangeClientRegistration(properties),
        configuration.mcpBackendExchangeAuthorizedClientProvider(properties));
  }

  private McpWorkloadProperties properties(String tokenPath) {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return new McpWorkloadProperties(
        URI.create(MCP_RESOURCE),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create(ISSUER),
        List.of("mcp.tools", "files.read"),
        URI.create(base + tokenPath),
        EDGE,
        jwkFile.toAbsolutePath(),
        URI.create(API_RESOURCE),
        URI.create(base + "/dav/files"),
        List.of("files.read"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(60),
        8192);
  }

  private McpCellWorkloadPrincipal workload() {
    return new McpCellWorkloadPrincipal(
        ISSUER,
        SUBJECT,
        CLIENT,
        Set.of("mcp.tools", "files.read"),
        now,
        now.plusSeconds(45),
        "cell-jti");
  }

  private static Map<String, String> form(String body) {
    Map<String, String> values = new LinkedHashMap<>();
    for (String pair : body.split("&")) {
      String[] parts = pair.split("=", 2);
      values.put(
          URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
          URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
    }
    return values;
  }
}
