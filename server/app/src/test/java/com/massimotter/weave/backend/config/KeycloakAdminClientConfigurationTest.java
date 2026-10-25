package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class KeycloakAdminClientConfigurationTest {
  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void sendsOnlyTheShortLivedBearerAndInvalidatesItAfterUnauthorized() throws Exception {
    AtomicReference<String> authorization = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/admin/realms/weave/organizations",
        exchange -> {
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          byte[] body = "denied".getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(401, body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();

    KeycloakAdminAccessTokenProvider tokens = mock(KeycloakAdminAccessTokenProvider.class);
    when(tokens.accessToken()).thenReturn("short-lived-token");
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties
        .keycloak()
        .setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    RestClient client =
        new KeycloakAdminClientConfiguration()
            .keycloakIdentityAdminRestClient(RestClient.builder(), tokens, properties);

    int status =
        client
            .get()
            .uri("/admin/realms/weave/organizations")
            .exchange((request, response) -> response.getStatusCode().value());

    assertThat(status).isEqualTo(401);
    assertThat(authorization).hasValue("Bearer short-lived-token");
    verify(tokens).invalidate("short-lived-token");
  }

  @Test
  void doesNotInvalidateAnAcceptedBearer() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/admin/realms/weave/organizations",
        exchange -> {
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    KeycloakAdminAccessTokenProvider tokens = mock(KeycloakAdminAccessTokenProvider.class);
    when(tokens.accessToken()).thenReturn("short-lived-token");
    IdentityInvitationProperties properties = new IdentityInvitationProperties();
    properties
        .keycloak()
        .setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    RestClient client =
        new KeycloakAdminClientConfiguration()
            .keycloakIdentityAdminRestClient(RestClient.builder(), tokens, properties);

    client.get().uri("/admin/realms/weave/organizations").retrieve().toBodilessEntity();

    verify(tokens, never()).invalidate("short-lived-token");
  }
}
