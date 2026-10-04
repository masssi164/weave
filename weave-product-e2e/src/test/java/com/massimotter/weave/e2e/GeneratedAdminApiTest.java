package com.massimotter.weave.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.adminapi.model.AdminControlPlaneResponse;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

final class GeneratedAdminApiTest {
  @Test
  void usesGeneratedAdminRouteAndDecodesTypedControlPlane() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/admin/control-plane",
        request -> {
          authorization.set(request.getRequestHeaders().getFirst("Authorization"));
          byte[] response =
              "{\"organizationId\":\"org-1\",\"supportSafe\":true}"
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
      AdminControlPlaneResponse result =
          new GeneratedAdminApi(origin, HttpClient.newBuilder()).controlPlane("admin-token");
      assertThat(authorization.get()).isEqualTo("Bearer admin-token");
      assertThat(result.getOrganizationId()).isEqualTo("org-1");
      assertThat(result.getSupportSafe()).isTrue();
    } finally {
      server.stop(0);
    }
  }

  @Test
  void generatedFailureDoesNotExposeResponseBody() throws Exception {
    HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/api/admin/control-plane",
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
              () -> new GeneratedAdminApi(origin, HttpClient.newBuilder()).controlPlane("admin-token"))
          .isInstanceOf(ProductFlowException.class)
          .hasMessage("Admin control plane failed with HTTP 403");
    } finally {
      server.stop(0);
    }
  }
}
