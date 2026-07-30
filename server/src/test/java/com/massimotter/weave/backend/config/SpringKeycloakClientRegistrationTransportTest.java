package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport.RegistrationHandoffOperation;
import com.massimotter.weave.backend.agentruntime.adapter.KeycloakClientRegistrationTransport.RegistrationHandoffProof;
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
  private static final String CLIENT_ID = "weaver-cell-test";
  private static final URI ISSUER =
      URI.create("https://auth.weave.test/realms/weave");

  private HttpServer server;
  private URI base;
  private AtomicInteger requests;
  private AtomicReference<String> authorization;
  private AtomicReference<String> body;
  private AtomicReference<URI> requestUri;
  private AtomicReference<String> handoffCapability;
  private AtomicReference<String> handoffState;
  private AtomicReference<String> handoffOperation;

  @BeforeEach
  void setUp() throws IOException {
    requests = new AtomicInteger();
    authorization = new AtomicReference<>();
    body = new AtomicReference<>();
    requestUri = new AtomicReference<>();
    handoffCapability = new AtomicReference<>();
    handoffState = new AtomicReference<>();
    handoffOperation = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          requestUri.set(exchange.getRequestURI());
          authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
          handoffCapability.set(
              exchange.getRequestHeaders().getFirst("Weave-Registration-Handoff"));
          handoffState.set(
              exchange.getRequestHeaders().getFirst(
                  "Weave-Registration-Handoff-State"));
          handoffOperation.set(
              exchange.getRequestHeaders().getFirst(
                  "Weave-Registration-Handoff-Operation"));
          body.set(
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          if ("Bearer rejected-administration-token".equals(authorization.get())) {
            respond(exchange, 401, "{\"error\":\"private rat-secret diagnostic\"}");
          } else if ("Bearer unsafe-handoff-error".equals(authorization.get())) {
            respond(exchange, 401, "{\"error\":\"invalid_token\"}");
          } else if ("Bearer scope-policy-token".equals(authorization.get())) {
            respond(
                exchange,
                403,
                "{\"error\":\"insufficient_scope\","
                    + "\"error_description\":\"Policy 'Allowed Client Scopes' rejected request\"}");
          } else if (exchange
              .getRequestURI()
              .getPath()
              .endsWith("/weave-registration-handoff/recover")) {
            noStore(exchange);
            respond(exchange, 200, "{}");
          } else if (exchange
              .getRequestURI()
              .getPath()
              .endsWith("/weave-registration-handoff/finalize")) {
            noStore(exchange);
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
          } else {
            respond(exchange, 200, "{}");
          }
        });
    server.start();
    base = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
  }

  @Test
  void reportsOnlyAnAllowlistedRegistrationPolicyFailureCategory() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));

    assertThatThrownBy(
            () ->
                transport.create(
                    new ObjectMapper().createObjectNode(),
                    "scope-policy-token",
                    proof(RegistrationHandoffOperation.CREATE)))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("failureType=RegistrationPolicyClientScopes")
        .hasMessageNotContaining("Allowed Client Scopes")
        .hasMessageNotContaining("scope-policy-token");
  }

  @AfterEach
  void tearDown() {
    server.stop(0);
  }

  @Test
  void bindsEveryOperationToTheConfiguredRegistrationAndTokenEndpoints() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));
    ObjectMapper mapper = new ObjectMapper();
    URI registration =
        URI.create(
            "https://auth.weave.test/realms/weave/clients-registrations/openid-connect/"
                + CLIENT_ID);

    transport.create(
        mapper.createObjectNode().put("client_name", "weaver-cell-test"),
        "administration-token",
        proof(RegistrationHandoffOperation.CREATE));
    assertThat(authorization).hasValue("Bearer administration-token");
    assertThat(handoffCapability)
        .hasValue(proof(RegistrationHandoffOperation.CREATE).capabilityHeader());
    assertThat(handoffOperation).hasValue("create");

    transport.retrieve(CLIENT_ID, registration, "rat-one".getBytes(StandardCharsets.UTF_8));
    assertThat(authorization).hasValue("Bearer rat-one");

    transport.update(
        CLIENT_ID,
        registration,
        mapper.createObjectNode().put("client_name", "weaver-cell-test"),
        "rat-two".getBytes(StandardCharsets.UTF_8),
        proof(RegistrationHandoffOperation.ROTATE));
    assertThat(authorization).hasValue("Bearer rat-two");
    assertThat(handoffOperation).hasValue("rotate");

    transport.clientCredentials(
        Map.of("grant_type", "client_credentials", "client_id", "weaver-cell-test"));
    assertThat(body.get())
        .contains("grant_type=client_credentials")
        .contains("client_id=weaver-cell-test");

    transport.delete(
        CLIENT_ID, registration, "rat-three".getBytes(StandardCharsets.UTF_8));
    assertThat(authorization).hasValue("Bearer rat-three");
    assertThat(requests).hasValue(5);

    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    base.resolve("/admin/realms/weave/clients"),
                    "rat-three".getBytes(StandardCharsets.UTF_8)))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThat(requests).hasValue(5);
  }

  @Test
  void rebindsOnlyTheConfiguredPublicRegistrationUriToTheInternalAuthority() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));
    URI publicRegistration =
        URI.create(
            "https://auth.weave.test/realms/weave/clients-registrations/openid-connect/"
                + "weaver-cell-test");

    transport.retrieve(
        CLIENT_ID, publicRegistration, "rat-one".getBytes(StandardCharsets.UTF_8));

    assertThat(requestUri)
        .hasValue(
            URI.create(
                "/realms/weave/clients-registrations/openid-connect/weaver-cell-test"));
    assertThat(authorization).hasValue("Bearer rat-one");
  }

  @Test
  void sendsTheClosedHandoffHeadersAndRequiresNonCacheableResponses() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));
    URI registration =
        URI.create(
            "https://auth.weave.test/realms/weave/clients-registrations/openid-connect/"
                + CLIENT_ID);
    RegistrationHandoffProof proof =
        proof(RegistrationHandoffOperation.ROTATE);

    transport.recover(CLIENT_ID, registration, "administration-token", proof);
    assertThat(body).hasValue("");
    assertThat(handoffCapability).hasValue(proof.capabilityHeader());
    assertThat(handoffState).hasValue(proof.stateDigest());
    assertThat(handoffOperation).hasValue("rotate");

    assertThat(
            transport.finalizeHandoff(
                CLIENT_ID,
                registration,
                "rat-current".getBytes(StandardCharsets.UTF_8),
                proof))
        .isEqualTo(
            com.massimotter.weave.backend.agentruntime.adapter
                .KeycloakClientRegistrationTransport.FinalizeResult.FINALIZED);
    assertThat(body).hasValue("");
    assertThat(authorization).hasValue("Bearer rat-current");
  }

  @Test
  void rejectsACacheableHandoffErrorResponse() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));
    URI registration =
        URI.create(
            "https://auth.weave.test/realms/weave/clients-registrations/openid-connect/"
                + CLIENT_ID);

    assertThatThrownBy(
            () ->
                transport.recover(
                    CLIENT_ID,
                    registration,
                    "unsafe-handoff-error",
                    proof(RegistrationHandoffOperation.CREATE)))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("unsafe registration handoff response")
        .hasMessageNotContaining("unsafe-handoff-error");
  }

  @Test
  void rejectsRegistrationUrisThatCouldEscapeTheConfiguredProtocolBoundary() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));
    byte[] token = "rat-one".getBytes(StandardCharsets.UTF_8);

    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    URI.create(
                        "https://auth.weave.test/realms/weave/clients-registrations/"
                            + "openid-connect/weaver-cell-test/other"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    URI.create(
                        "https://auth.weave.test/realms/weave/clients-registrations/"
                            + "openid-connect/weaver-cell-test%2Fother"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    URI.create(
                        "https://auth.weave.test/realms/weave/clients-registrations/"
                            + "openid-connect/not-a-workload"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    URI.create(
                        "https://auth.weave.test/realms/weave/clients-registrations/"
                            + "openid-connect/weaver-cell-test?destination=elsewhere"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThatThrownBy(
            () ->
                transport.retrieve(
                    CLIENT_ID,
                    URI.create(
                        "https://foreign.example/realms/weave/clients-registrations/"
                            + "openid-connect/weaver-cell-test"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThatThrownBy(
            () ->
                transport.retrieve(
                    "weaver-cell-other",
                    URI.create(
                        "https://auth.weave.test/realms/weave/clients-registrations/"
                            + "openid-connect/weaver-cell-test"),
                    token))
        .isInstanceOf(RuntimeWorkloadIdentityException.class)
        .hasMessageContaining("outside the configured realm boundary");
    assertThat(requests).hasValue(0);
  }

  @Test
  void withholdsProviderBodiesAndCredentialValuesOnFailure() {
    SpringKeycloakClientRegistrationTransport transport =
        new SpringKeycloakClientRegistrationTransport(
            base, ISSUER, "weave", Duration.ofSeconds(2));

    assertThatThrownBy(
            () ->
                transport.create(
                    new ObjectMapper().createObjectNode(),
                    "rejected-administration-token",
                    proof(RegistrationHandoffOperation.CREATE)))
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

  private static void noStore(HttpExchange exchange) {
    exchange.getResponseHeaders().set("Cache-Control", "no-store");
    exchange.getResponseHeaders().set("Pragma", "no-cache");
  }

  private static RegistrationHandoffProof proof(
      RegistrationHandoffOperation operation) {
    return new RegistrationHandoffProof(
        new byte[32],
        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
        operation);
  }
}
