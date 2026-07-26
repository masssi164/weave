package com.massimotter.weave.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class FilesWebDavClientTest {
  private HttpServer server;
  private AtomicReference<String> searchBody;
  private AtomicReference<String> authorization;

  @BeforeEach
  void setUp() throws Exception {
    searchBody = new AtomicReference<>();
    authorization = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void resourceReadResolvesCanonicalIdByExactDavPredicateThenGetsFacadeContent() {
    server.createContext(
        "/dav/files",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          searchBody.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response = searchResponse("files:/Team/readme.md");
          exchange.getResponseHeaders().set("Content-Type", "application/xml");
          exchange.sendResponseHeaders(207, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.createContext(
        "/dav/files/Team/readme.md",
        exchange -> {
          byte[] response = "hello".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();

    FilesWebDavClient.FileContent result = client().read("files:/Team/readme.md");

    assertThat(authorization.get()).isEqualTo("Bearer exchanged-only");
    assertThat(searchBody.get())
        .contains("<d:eq>")
        .contains("<w:canonical-id/>")
        .contains("<d:literal>files:/Team/readme.md</d:literal>")
        .doesNotContain("<d:like>");
    assertThat(result.content()).isEqualTo("hello".getBytes(StandardCharsets.UTF_8));
  }

  @Test
  void resourceReadFailsClosedWhenCanonicalIdResolutionIsAmbiguous() {
    server.createContext(
        "/dav/files",
        exchange -> {
          byte[] first = searchResponse("files:/Team/readme.md");
          String xml = new String(first, StandardCharsets.UTF_8);
          byte[] response =
              xml.replace(
                      "</d:multistatus>",
                      xml.substring(
                              xml.indexOf("<d:response>"),
                              xml.indexOf("</d:response>") + "</d:response>".length())
                          + "</d:multistatus>")
                  .getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(207, response.length);
          exchange.getResponseBody().write(response);
          exchange.close();
        });
    server.start();

    assertThatThrownBy(() -> client().read("files:/Team/readme.md"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ambiguous");
  }

  private FilesWebDavClient client() {
    McpInvocationCredentials credentials = mock(McpInvocationCredentials.class);
    when(credentials.exchangedBearer()).thenReturn("exchanged-only");
    return new FilesWebDavClient(properties(), RestClient.builder(), credentials);
  }

  private McpWorkloadProperties properties() {
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    return new McpWorkloadProperties(
        URI.create("https://api.weave.test/mcp"),
        URI.create("https://api.weave.test/.well-known/oauth-protected-resource/mcp"),
        URI.create("https://auth.weave.test/realms/weave"),
        List.of("mcp:tools", "files.read"),
        URI.create(base + "/token"),
        "weave-mcp-server",
        Path.of("/tmp/not-read.jwk"),
        URI.create("https://api.weave.test/api"),
        URI.create(base + "/dav/files"),
        List.of("files.read"),
        Duration.ofSeconds(2),
        Duration.ofSeconds(60),
        8192);
  }

  private byte[] searchResponse(String canonicalId) {
    return """
    <?xml version="1.0" encoding="UTF-8"?>
    <d:multistatus xmlns:d="DAV:" xmlns:w="urn:weave:files">
      <d:response>
        <d:href>/dav/files/Team/readme.md</d:href>
        <d:propstat><d:prop>
          <w:canonical-id>%s</w:canonical-id>
          <d:displayname>readme.md</d:displayname>
          <d:getcontenttype>text/plain</d:getcontenttype>
          <d:getcontentlength>5</d:getcontentlength>
        </d:prop></d:propstat>
      </d:response>
    </d:multistatus>
    """
        .formatted(canonicalId)
        .getBytes(StandardCharsets.UTF_8);
  }
}
