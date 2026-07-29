package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpringKeycloakClientRegistrationTransportTest {
  private HttpServer server;
  private URI base;
  private AtomicInteger requests;
  private AtomicReference<String> authorization;
  private AtomicReference<String> body;

  @BeforeEach
  void setUp() throws IOException {
    requests = new AtomicInteger();
    authorization = new AtomicReference<>();
    body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          body.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          if ("Bearer rejected-administration-token".equals(authorization.get())) {
            respond(exchange, 401, "{\"error\":\"private rat-secret diagnostic\"}");
          } else {
            respond(exchange, 200, "{}");
          }
        });
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void bindsEveryOperationToTheConfiguredRegistrationAndTokenEndpoints() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, "weave", Duration.ofSeconds(2));
    ObjectMapper mapper = new ObjectMapper();
    URI registration =
        base.resolve(
            "/realms/weave/clients-registrations/openid-connect/weaver-cell-test");

    transport.create(
        mapper.createObjectNode().put("client_name", "weaver-cell-test"),
        "administration-token");
    assertThat(authorization).hasValue("Bearer administration-token");

    transport.retrieve(registration, "rat-one".getBytes(StandardCharsets.UTF_8));
    assertThat(authorization).hasValue("Bearer rat-one");

    transport.update(
        registration,
        mapper.createObjectNode().put("client_name", "weaver-cell-test"),
        "rat-two".getBytes(StandardCharsets.UTF_8));
    assertThat(authorization).hasValue("Bearer rat-two");

    transport.clientCredentials(
        Map.of("grant_type", "client_credentials", "client_id", "weaver-cell-test"));
    assertThat(body.get())
        .contains("grant_type=client_credentials")
        .contains("client_id=weaver-cell-test");

    transport.delete(registration, "rat-three".getBytes(StandardCharsets.UTF_8));
    assertThat(authorization).hasValue("Bearer rat-three");
    assertThat(requests).hasValue(5);

    assertThatThrownBy(
            () ->
                transport.retrieve(
                    base.resolve("/admin/realms/weave/clients"),
                    "rat-three".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThat(requests).hasValue(5);
  }

  @Test
  void withholdsProviderBodiesAndCredentialValuesOnFailure() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, "weave", Duration.ofSeconds(2));

    assertThatThrownBy(
            () ->
                transport.create(
                    new ObjectMapper().createObjectNode(),
                    "rejected-administration-token"))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("protocol request failed")
        .hasMessageNotContaining("rat-secret")
        .hasMessageNotContaining("rejected-administration-token");
  }

  private static void respond(HttpExchange exchange, int status, String value)
      throws IOException {
    byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
