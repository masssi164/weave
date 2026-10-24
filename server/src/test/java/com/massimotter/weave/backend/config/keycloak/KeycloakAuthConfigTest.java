package com.massimotter.weave.backend.config.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.massimotter.weave.backend.agentruntime.adapter.KeycloakAdminAccessTokenProvider;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.massimotter.weave.backend.identity.invitation.KeycloakIdentityAdminClient.KeycloakAdminException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class KeycloakAuthConfigTest {
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
        new KeycloakAuthConfig()
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
        new KeycloakAuthConfig()
            .keycloakIdentityAdminRestClient(RestClient.builder(), tokens, properties);

    client.get().uri("/admin/realms/weave/organizations").retrieve().toBodilessEntity();

    verify(tokens, never()).invalidate("short-lived-token");
  }

  @Test
  void declarativeProxyPreservesTheActualUnexpectedHttpStatusForPolicyChecks() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/admin/realms/weave/organizations", exchange -> {
      exchange.sendResponseHeaders(202, -1);
      exchange.close();
    });
    server.start();

    var tokens = mock(KeycloakAdminAccessTokenProvider.class);
    when(tokens.accessToken()).thenReturn("short-lived-token");
    var properties = new IdentityInvitationProperties();
    properties.keycloak().setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    RestClient transport = new KeycloakAuthConfig()
        .keycloakIdentityAdminRestClient(RestClient.builder(), tokens, properties);
    KeycloakAdminClient client = KeycloakAuthConfig.proxy(transport, properties.keycloak().baseUrl());

    assertThatThrownBy(() -> client.organizations("weave", 0, 100))
        .isInstanceOf(KeycloakAdminException.class)
        .satisfies(failure -> assertThat(((KeycloakAdminException) failure).status()).isEqualTo(202));
    verify(tokens, never()).invalidate("short-lived-token");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://identity.internal/auth", "https://identity.internal/auth/"})
  void keepsTheConfiguredContextPath(String baseUrl) {
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    provider.expect(requestTo(baseUrl.replaceAll("/+$", "")
            + "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(KeycloakAuthConfig.proxy(builder.build(), URI.create(baseUrl)).humanUsers("weave"))
        .isEqualTo("[]");
    provider.verify();
  }

  @ParameterizedTest
  @ValueSource(ints = {202, 401, 404, 503})
  void rejectsUnexpectedStatusesBeforeReadingProviderBody(int status) throws Exception {
    URI baseUrl = URI.create("https://identity.internal/auth");
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
    provider.expect(requestTo(baseUrl
            + "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true"))
        .andRespond(request -> response);

    assertThatThrownBy(() -> KeycloakAuthConfig.proxy(builder.build(), baseUrl).humanUsers("weave"))
        .isInstanceOf(KeycloakAdminException.class)
        .satisfies(failure -> assertThat(((KeycloakAdminException) failure).status()).isEqualTo(status));
    verify(response, never()).getBody();
    verify(response).close();
    provider.verify();
  }

  @Test
  void deniesManagedCredentialMethodsWithoutTheirSeparateAdmissionBeforeTransport() {
    URI baseUrl = URI.create("https://identity.internal");
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    KeycloakAdminClient client = KeycloakAuthConfig.proxy(builder.build(), baseUrl);

    assertThatThrownBy(() -> client.managedCredentials("weave", "subject-1", false))
        .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("guarded boundary");
    provider.verify();
  }

  @Test
  void pathVariablesCannotSelectAnAdditionalRouteSegment() {
    URI baseUrl = URI.create("https://identity.internal/auth");
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    provider.expect(requestTo(baseUrl + "/admin/realms/weave/users/subject%2Fsegment"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    KeycloakAuthConfig.proxy(builder.build(), baseUrl).user("weave", "subject/segment");
    provider.verify();
  }
}
