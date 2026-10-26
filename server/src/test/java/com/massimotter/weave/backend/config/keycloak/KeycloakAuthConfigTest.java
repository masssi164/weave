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
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;

class KeycloakAuthConfigTest {
  @TempDir Path temporary;

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void productionGroupComposesPolicyAuthenticationInvalidationAndIsolation() throws Exception {
    AtomicInteger tokenRequests = new AtomicInteger();
    AtomicInteger adminRequests = new AtomicInteger();
    AtomicReference<String> firstAuthorization = new AtomicReference<>();
    AtomicReference<String> secondAuthorization = new AtomicReference<>();
    AtomicReference<String> unrelatedAuthorization = new AtomicReference<>();

    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/realms/weave/protocol/openid-connect/token",
        exchange -> {
          int sequence = tokenRequests.incrementAndGet();
          respond(
              exchange,
              200,
              "{\"access_token\":\"admin-token-"
                  + sequence
                  + "\",\"token_type\":\"Bearer\",\"expires_in\":60}");
        });
    server.createContext(
        "/admin/realms/weave/organizations",
        exchange -> {
          int sequence = adminRequests.incrementAndGet();
          String authorization = exchange.getRequestHeaders().getFirst("Authorization");
          if (sequence == 1) {
            firstAuthorization.set(authorization);
            respond(exchange, 401, "denied");
          } else if (sequence == 2) {
            secondAuthorization.set(authorization);
            respond(exchange, 200, "[]");
          } else {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
          }
        });
    server.createContext(
        "/unrelated",
        exchange -> {
          unrelatedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          exchange.sendResponseHeaders(204, -1);
          exchange.close();
        });
    server.start();

    URI baseUrl = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    Path secret = writePrivateJwk();
    IdentityInvitationProperties properties = properties(baseUrl, secret);

    new ApplicationContextRunner()
        .withConfiguration(
            AutoConfigurations.of(
                HttpServiceClientPropertiesAutoConfiguration.class,
                ImperativeHttpClientAutoConfiguration.class,
                RestClientAutoConfiguration.class,
                HttpServiceClientAutoConfiguration.class))
        .withUserConfiguration(KeycloakAuthConfig.class)
        .withBean(IdentityInvitationProperties.class, () -> properties)
        .run(
            context -> {
              assertThat(context).hasNotFailed().hasSingleBean(KeycloakAdminClient.class);
              KeycloakAdminClient client = context.getBean(KeycloakAdminClient.class);

              assertThatThrownBy(() -> client.organizations("weave", 0, 100, true))
                  .isInstanceOf(KeycloakAdminException.class)
                  .satisfies(
                      failure ->
                          assertThat(((KeycloakAdminException) failure).status()).isEqualTo(401));
              assertThat(firstAuthorization).hasValue("Bearer admin-token-1");

              assertThat(client.organizations("weave", 0, 100, true).isArray()).isTrue();
              assertThat(secondAuthorization).hasValue("Bearer admin-token-2");
              assertThat(tokenRequests).hasValue(2);

              assertThatThrownBy(() -> client.organizations("weave", 0, 100, true))
                  .isInstanceOf(KeycloakAdminException.class)
                  .satisfies(
                      failure ->
                          assertThat(((KeycloakAdminException) failure).status()).isEqualTo(202));
              assertThat(tokenRequests).hasValue(2);

              context
                  .getBean(RestClient.Builder.class)
                  .build()
                  .get()
                  .uri(baseUrl.resolve("/unrelated"))
                  .retrieve()
                  .toBodilessEntity();
              assertThat(unrelatedAuthorization.get()).isNull();
            });
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
    RestClient.Builder builder =
        RestClient.builder().baseUrl(properties.keycloak().baseUrl().toString());
    KeycloakAuthConfig.configureTransport(builder, tokens, properties.keycloak());
    RestClient client = builder.build();

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
    RestClient.Builder builder =
        RestClient.builder().baseUrl(properties.keycloak().baseUrl().toString());
    KeycloakAuthConfig.configureTransport(builder, tokens, properties.keycloak());
    RestClient client = builder.build();

    client.get().uri("/admin/realms/weave/organizations").retrieve().toBodilessEntity();

    verify(tokens, never()).invalidate("short-lived-token");
  }

  @Test
  void declarativeProxyPreservesTheActualUnexpectedHttpStatusForPolicyChecks() throws Exception {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/admin/realms/weave/organizations",
        exchange -> {
          exchange.sendResponseHeaders(202, -1);
          exchange.close();
        });
    server.start();

    var tokens = mock(KeycloakAdminAccessTokenProvider.class);
    when(tokens.accessToken()).thenReturn("short-lived-token");
    var properties = new IdentityInvitationProperties();
    properties
        .keycloak()
        .setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
    RestClient.Builder builder = RestClient.builder();
    KeycloakAuthConfig.configurePolicy(builder, properties.keycloak().baseUrl());
    KeycloakAuthConfig.configureTransport(builder, tokens, properties.keycloak());
    KeycloakAdminClient client = KeycloakAdminTestClient.proxy(builder.build());

    assertThatThrownBy(() -> client.organizations("weave", 0, 100, true))
        .isInstanceOf(KeycloakAdminException.class)
        .satisfies(
            failure ->
                assertThat(((KeycloakAdminException) failure).status()).isEqualTo(202));
    verify(tokens, never()).invalidate("short-lived-token");
  }

  @ParameterizedTest
  @ValueSource(strings = {"https://identity.internal/auth", "https://identity.internal/auth/"})
  void keepsTheConfiguredContextPath(String baseUrl) {
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    provider
        .expect(
            requestTo(
                baseUrl.replaceAll("/+$", "")
                    + "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true"))
        .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    KeycloakAuthConfig.configurePolicy(builder, URI.create(baseUrl));
    JsonNode users =
        KeycloakAdminTestClient.proxy(builder.build()).humanUsers("weave", 0, 1, true);
    assertThat(users.isArray()).isTrue();
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
    provider
        .expect(
            requestTo(
                baseUrl
                    + "/admin/realms/weave/users?first=0&max=1&briefRepresentation=true"))
        .andRespond(request -> response);

    KeycloakAuthConfig.configurePolicy(builder, baseUrl);
    assertThatThrownBy(
            () ->
                KeycloakAdminTestClient.proxy(builder.build())
                    .humanUsers("weave", 0, 1, true))
        .isInstanceOf(KeycloakAdminException.class)
        .satisfies(
            failure ->
                assertThat(((KeycloakAdminException) failure).status()).isEqualTo(status));
    verify(response, never()).getBody();
    verify(response).close();
    provider.verify();
  }

  @Test
  void deniesManagedCredentialMethodsWithoutTheirSeparateAdmissionBeforeTransport() {
    URI baseUrl = URI.create("https://identity.internal");
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    KeycloakAuthConfig.configurePolicy(builder, baseUrl);
    KeycloakAdminClient client = KeycloakAdminTestClient.proxy(builder.build());

    assertThatThrownBy(() -> client.managedCredentials("weave", "subject-1", false))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("guarded boundary");
    provider.verify();
  }

  @Test
  void pathVariablesCannotSelectAnAdditionalRouteSegment() {
    URI baseUrl = URI.create("https://identity.internal/auth");
    RestClient.Builder builder = RestClient.builder().baseUrl(baseUrl);
    var provider = MockRestServiceServer.bindTo(builder).build();
    provider
        .expect(requestTo(baseUrl + "/admin/realms/weave/users/subject%2Fsegment"))
        .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

    KeycloakAuthConfig.configurePolicy(builder, baseUrl);
    KeycloakAdminTestClient.proxy(builder.build()).user("weave", "subject/segment");
    provider.verify();
  }

  private IdentityInvitationProperties properties(URI baseUrl, Path secret) {
    var properties = new IdentityInvitationProperties();
    properties.keycloak().setBaseUrl(baseUrl);
    properties.keycloak().setAdminPrivateJwkFile(secret);
    properties.keycloak().setPrivateKeyJwtAudience(baseUrl.resolve("/realms/weave"));
    properties.keycloak().setTimeout(java.time.Duration.ofSeconds(2));
    return properties;
  }

  private Path writePrivateJwk() throws Exception {
    Path secret = temporary.resolve("weave-identity-admin-private-jwk.json");
    Files.writeString(
        secret,
        new RSAKeyGenerator(2048)
            .keyID("identity-admin-group-test")
            .algorithm(JWSAlgorithm.PS256)
            .generate()
            .toJSONString(),
        StandardCharsets.UTF_8);
    if (Files.getFileStore(secret).supportsFileAttributeView("posix")) {
      Files.setPosixFilePermissions(secret, PosixFilePermissions.fromString("rw-------"));
    }
    return secret;
  }

  private static void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
