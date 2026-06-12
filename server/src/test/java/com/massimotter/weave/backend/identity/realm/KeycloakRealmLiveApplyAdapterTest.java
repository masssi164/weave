package com.massimotter.weave.backend.identity.realm;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakRealmLiveApplyAdapterTest {

    private HttpServer server;
    private final List<String> requests = new ArrayList<>();
    private final List<String> authorizations = new ArrayList<>();

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void liveApplyPerformsMinimalKeycloakAdminRestSliceWithSupportSafeResult() throws Exception {
        startServer(exchange -> {
            String methodAndPath = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            requests.add(methodAndPath);
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            if (methodAndPath.equals("GET /admin/realms/weave-dogfood")) {
                respond(exchange, 404, "{}");
            } else if (methodAndPath.equals("POST /admin/realms")) {
                respond(exchange, 201, "{}");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/roles/admin")) {
                respond(exchange, 404, "{}");
            } else if (methodAndPath.equals("POST /admin/realms/weave-dogfood/roles")) {
                respond(exchange, 201, "{}");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/groups?search=weave-board-editors")) {
                respond(exchange, 200, "[]");
            } else if (methodAndPath.equals("POST /admin/realms/weave-dogfood/groups")) {
                respond(exchange, 201, "{}");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/clients?clientId=weave-app")) {
                respond(exchange, 200, "[]");
            } else if (methodAndPath.equals("POST /admin/realms/weave-dogfood/clients")) {
                respond(exchange, 201, "{}");
            } else {
                respond(exchange, 500, "unexpected");
            }
        });
        IdentityRealmApplyProperties properties = properties();
        KeycloakRealmLiveApplyAdapter adapter = new KeycloakRealmLiveApplyAdapter(properties);

        var result = adapter.apply(null, request(desiredState()));

        assertThat(result.applied()).isTrue();
        assertThat(result.providerMutationPerformed()).isTrue();
        assertThat(result.executionMode()).isEqualTo("guarded-keycloak-live-apply");
        assertThat(result.blockedReasons()).isEmpty();
        assertThat(requests).contains(
                "POST /admin/realms",
                "POST /admin/realms/weave-dogfood/roles",
                "POST /admin/realms/weave-dogfood/groups",
                "POST /admin/realms/weave-dogfood/clients");
        assertThat(authorizations).allMatch("Bearer token-that-must-not-leak"::equals);
        assertThat(String.join(" ", result.nextActions()))
                .doesNotContain("token-that-must-not-leak", "Authorization", baseUri().toString());
    }

    @Test
    void liveApplyCanProveNoopWithoutClaimingProviderMutation() throws Exception {
        startServer(exchange -> {
            String methodAndPath = exchange.getRequestMethod() + " " + exchange.getRequestURI();
            requests.add(methodAndPath);
            if (methodAndPath.equals("GET /admin/realms/weave-dogfood")) {
                respond(exchange, 200, "{\"displayName\":\"Weave Dogfood\",\"enabled\":true}");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/roles/admin")) {
                respond(exchange, 200, "{\"name\":\"admin\"}");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/groups?search=weave-board-editors")) {
                respond(exchange, 200, "[{\"name\":\"weave-board-editors\"}]");
            } else if (methodAndPath.equals("GET /admin/realms/weave-dogfood/clients?clientId=weave-app")) {
                respond(exchange, 200, "[{\"id\":\"client-123\",\"clientId\":\"weave-app\",\"publicClient\":true,\"redirectUris\":[\"https://weave.test/callback\"],\"defaultClientScopes\":[\"openid\"]}]");
            } else {
                respond(exchange, 500, "unexpected");
            }
        });
        KeycloakRealmLiveApplyAdapter adapter = new KeycloakRealmLiveApplyAdapter(properties());

        var result = adapter.apply(null, request(desiredState()));

        assertThat(result.applied()).isTrue();
        assertThat(result.providerMutationPerformed()).isFalse();
        assertThat(result.executionMode()).isEqualTo("guarded-keycloak-live-apply-noop");
        assertThat(requests).noneMatch(request -> request.startsWith("POST ") || request.startsWith("PUT "));
    }

    @Test
    void liveApplyBlocksProviderUnavailableWithoutMutationClaim() throws Exception {
        startServer(exchange -> respond(exchange, 503, "provider unavailable and token-like raw details ignored"));
        KeycloakRealmLiveApplyAdapter adapter = new KeycloakRealmLiveApplyAdapter(properties());

        var result = adapter.apply(null, request(desiredState()));

        assertThat(result.applied()).isFalse();
        assertThat(result.providerMutationPerformed()).isFalse();
        assertThat(result.executionMode()).isEqualTo("guarded-provider-live-apply-unavailable");
        assertThat(String.join(" ", result.blockedReasons()) + String.join(" ", result.nextActions()))
                .doesNotContain("token-like", "Authorization", "token-that-must-not-leak", baseUri().toString());
    }

    private void startServer(ExchangeHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Throwable throwable) {
                respond(exchange, 500, "handler failure");
            }
        });
        server.start();
    }

    private IdentityRealmApplyProperties properties() {
        IdentityRealmApplyProperties properties = new IdentityRealmApplyProperties();
        properties.setLiveApplyEnabled(true);
        properties.setProviderConfigured(true);
        properties.setKeycloakAdminBaseUrl(baseUri().toString());
        properties.setKeycloakAdminToken("token-that-must-not-leak");
        return properties;
    }

    private URI baseUri() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private IdentityRealmApplyRequest request(IdentityRealmDesiredState desiredState) {
        return new IdentityRealmApplyRequest(
                desiredState,
                desiredState,
                "realm-dry-run-123",
                "effective-policy-simulation-123",
                "APPLY WEAVE IDENTITY REALM",
                false,
                false,
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                null,
                "support-safe test");
    }

    private IdentityRealmDesiredState desiredState() {
        return new IdentityRealmDesiredState(
                "weave-dogfood",
                "Weave Dogfood",
                true,
                List.of(new IdentityRealmDesiredState.RealmClient(
                        "weave-app",
                        true,
                        List.of("https://weave.test/callback"),
                        List.of("admin"),
                        List.of("openid"))),
                List.of("admin"),
                List.of("weave-board-editors"),
                List.of("openid"),
                List.of(),
                List.of("https://weave.test/callback"),
                List.of(),
                List.of(),
                List.of(new IdentityRealmDesiredState.RecoveryIdentity("issuer+subject:https://auth.example.invalid/realms/weave#admin-123", "last-admin recovery", true, List.of("owner"))),
                List.of("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"),
                "sub",
                List.of(),
                List.of());
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
