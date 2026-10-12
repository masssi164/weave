package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FilesUserApiClientTest {
  private HttpServer server;
  private final AtomicReference<String> authorization = new AtomicReference<>();
  private final AtomicReference<String> method = new AtomicReference<>();

  @BeforeEach
  void setUp() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void searchUsesGeneratedUserListAndStableFileReferences() {
    server.createContext("/api/files/items", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      method.set(exchange.getRequestMethod());
      String query = exchange.getRequestURI().getRawQuery();
      String body = query == null
          ? listing("file:root", folder())
          : listing("file:folder", item());
      reply(exchange, 200, body);
    });
    server.start();

    var matches = client().search("README", "/Team", 10);

    assertThat(method.get()).isEqualTo("GET");
    assertThat(authorization.get()).isEqualTo("Bearer exchanged-only");
    assertThat(matches).singleElement().satisfies(match -> {
      assertThat(match.canonicalFileId()).isEqualTo("file:readme");
      assertThat(match.path()).isEqualTo("/Team/readme.md");
      assertThat(match.name()).isEqualTo("readme.md");
    });
  }

  @Test
  void resourceReadUsesGeneratedMetadataAndBinaryOperations() {
    AtomicInteger metadataReads = new AtomicInteger();
    server.createContext("/api/files/items/", exchange -> {
      authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
      if (exchange.getRequestURI().getPath().endsWith("/content")) {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/plain");
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
      } else {
        metadataReads.incrementAndGet();
        reply(exchange, 200, item());
      }
    });
    server.start();

    var result = client().read("file:readme");

    assertThat(authorization.get()).isEqualTo("Bearer exchanged-only");
    assertThat(metadataReads.get()).isEqualTo(2);
    assertThat(result.item().canonicalFileId()).isEqualTo("file:readme");
    assertThat(result.content()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void changedRevisionFailsClosedAfterBinaryRead() {
    AtomicInteger metadataReads = new AtomicInteger();
    server.createContext("/api/files/items/", exchange -> {
      if (exchange.getRequestURI().getPath().endsWith("/content")) {
        byte[] body = "hello".getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        try (var output = exchange.getResponseBody()) { output.write(body); }
      } else {
        reply(exchange, 200, item().replace("revision-1", "revision-2" + metadataReads.getAndIncrement()));
      }
    });
    server.start();

    assertThatThrownBy(() -> client().read("file:readme"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("The file changed during MCP read");
  }

  @Test
  void downstreamErrorsNeverExposeResponseBodies() {
    server.createContext("/api/files/items", exchange ->
        reply(exchange, 403, "{\"access_token\":\"must-not-escape\",\"detail\":\"private\"}"));
    server.start();

    assertThatThrownBy(() -> client().search("readme", "/", 10))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Files User API rejected request: HTTP 403")
        .hasMessageNotContaining("access_token")
        .hasMessageNotContaining("must-not-escape")
        .hasMessageNotContaining("private");
  }

  private FilesUserApiClient client() {
    McpInvocationCredentials credentials = mock(McpInvocationCredentials.class);
    when(credentials.exchangedBearer()).thenReturn("exchanged-only");
    return new FilesUserApiClient(properties(), credentials);
  }

  private McpWorkloadProperties properties() {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return new McpWorkloadProperties(
        URI.create("https://api.weave.test/mcp"),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create("https://auth.weave.test/realms/weave"),
        List.of("mcp.tools", "files.read"),
        URI.create(base + "/token"),
        "weave-mcp-server",
        Path.of("/tmp/not-read.jwk"),
        URI.create("https://api.weave.test/api"),
        URI.create(base + "/api"),
        List.of("files.read"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(60),
        8192);
  }

  private static String listing(String parent, String... items) {
    return "{\"parentFileId\":\"" + parent + "\",\"allowedActions\":[\"listChildren\"],\"items\":["
        + String.join(",", items) + "]}";
  }

  private static String folder() {
    return "{\"fileId\":\"file:folder\",\"parentFileId\":\"file:root\",\"name\":\"Team\","
        + "\"displayPath\":\"/Team\",\"kind\":\"folder\",\"size\":0,"
        + "\"revision\":\"revision-folder\",\"allowedActions\":[\"inspect\",\"listChildren\"]}";
  }

  private static String item() {
    return "{\"fileId\":\"file:readme\",\"parentFileId\":\"file:folder\",\"name\":\"readme.md\","
        + "\"displayPath\":\"/Team/readme.md\",\"kind\":\"file\",\"size\":5,"
        + "\"mediaType\":\"text/plain\",\"revision\":\"revision-1\","
        + "\"allowedActions\":[\"inspect\",\"download\"]}";
  }

  private static void reply(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    try (var output = exchange.getResponseBody()) { output.write(bytes); }
  }
}
