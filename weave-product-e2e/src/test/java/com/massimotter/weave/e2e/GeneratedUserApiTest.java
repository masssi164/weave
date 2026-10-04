package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.userapi.model.AuthenticatedUserResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GeneratedUserApiTest {
  @Test
  void usesGeneratedUserRouteAndDecodesTypedIdentity() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/me",
        request -> {
          authorization.set(request.getRequestHeaders().getFirst("Authorization"));
          byte[] response =
              "{\"identityIssuer\":\"https://auth.example.test\",\"subject\":\"member-1\",\"organizationId\":\"org-1\"}"
                  .getBytes(StandardCharsets.UTF_8);
          request.getResponseHeaders().set("Content-Type", "application/json");
          request.sendResponseHeaders(200, response.length);
          try (var body = request.getResponseBody()) {
            body.write(response);
          }
        });
    server.start();
    try {
      URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      AuthenticatedUserResponse user =
          new GeneratedUserApi(origin, HttpClient.newBuilder()).authenticatedUser("test-token");
      assertThat(authorization.get()).isEqualTo("Bearer test-token");
      assertThat(user.getIdentityIssuer()).isEqualTo("https://auth.example.test");
      assertThat(user.getSubject()).isEqualTo("member-1");
      assertThat(user.getOrganizationId()).isEqualTo("org-1");
    } finally {
      server.stop(0);
    }
  }

  @Test
  void generatedFailureDoesNotExposeResponseBody() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/me",
        request -> {
          byte[] response = "secret-provider-diagnostic".getBytes(StandardCharsets.UTF_8);
          request.sendResponseHeaders(403, response.length);
          try (var body = request.getResponseBody()) {
            body.write(response);
          }
        });
    server.start();
    try {
      URI origin = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
      assertThatThrownBy(
              () -> new GeneratedUserApi(origin, HttpClient.newBuilder()).authenticatedUser("test-token"))
          .isInstanceOf(ProductFlowException.class)
          .hasMessage("authenticated member surface failed with HTTP 403");
    } finally {
      server.stop(0);
    }
  }
}
