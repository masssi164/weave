package com.massimotter.weave.backend.identity.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.config.IdentityInvitationProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakIdentityAdminClientTest {
    private HttpServer server;
    private final AtomicReference<String> tokenAuthorization = new AtomicReference<>();
    private final AtomicReference<String> tokenBody = new AtomicReference<>();
    private final List<String> requests = new ArrayList<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void authenticatesWithHttpBasicAndResolvesTheExactNestedGroupPath() {
        client().applyRoleAndGroups("subject-1", "member", List.of("/weave/members"));

        assertThat(tokenAuthorization).hasValue("Basic " + Base64.getEncoder()
                .encodeToString("weave-identity-admin:identity-secret".getBytes(StandardCharsets.UTF_8)));
        assertThat(tokenBody).hasValue("grant_type=client_credentials");
        assertThat(requests).containsExactly(
                "GET /admin/realms/weave/groups?search=weave&exact=true&first=0&max=2",
                "GET /admin/realms/weave/groups/root-uuid/children?first=0&max=100",
                "GET /admin/realms/weave/clients?clientId=weave-app",
                "GET /admin/realms/weave/clients/client-uuid/roles/member",
                "POST /admin/realms/weave/users/subject-1/role-mappings/clients/client-uuid",
                "PUT /admin/realms/weave/users/subject-1/groups/members-uuid");
    }

    @Test
    void rejectsLegacyOrWorkloadGroupNamesBeforeAnyProviderMutation() {
        KeycloakIdentityAdminClient client = client();

        assertThatThrownBy(() -> client.applyRoleAndGroups("subject-1", "member", List.of("workspace-members")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical human group path");
        assertThatThrownBy(() -> client.applyRoleAndGroups("subject-1", "member", List.of("/weave/weaver-runtime")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("canonical human group path");
        assertThatThrownBy(() -> client.applyRoleAndGroups("subject-1", "member", List.of("/weave/owners")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("match the selected canonical human role");
        assertThat(requests).isEmpty();
        assertThat(tokenAuthorization).hasValue(null);
    }

    private KeycloakIdentityAdminClient client() {
        IdentityInvitationProperties properties = new IdentityInvitationProperties();
        properties.keycloak().setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        properties.keycloak().setRealm("weave");
        properties.keycloak().setClientId("weave-identity-admin");
        properties.keycloak().setClientSecret("identity-secret");
        properties.keycloak().setTimeout(Duration.ofSeconds(2));
        return new KeycloakIdentityAdminClient(
                properties,
                new ObjectMapper().findAndRegisterModules(),
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build(),
                Clock.fixed(Instant.parse("2026-07-22T10:00:00Z"), ZoneOffset.UTC));
    }

    private void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String query = exchange.getRequestURI().getRawQuery();
        if (path.equals("/realms/weave/protocol/openid-connect/token")) {
            tokenAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            tokenBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"access_token\":\"admin-token\",\"expires_in\":60}");
            return;
        }
        requests.add(exchange.getRequestMethod() + " " + path + (query == null ? "" : "?" + query));
        if (path.equals("/admin/realms/weave/groups") && "search=weave&exact=true&first=0&max=2".equals(query)) {
            respond(exchange, 200, "[{\"id\":\"root-uuid\",\"name\":\"weave\",\"path\":\"/weave\"}]");
        } else if (path.equals("/admin/realms/weave/groups/root-uuid/children")) {
            respond(exchange, 200, "["
                    + "{\"id\":\"other-uuid\",\"name\":\"members\",\"path\":\"/other/members\"},"
                    + "{\"id\":\"members-uuid\",\"name\":\"members\",\"path\":\"/weave/members\"}]");
        } else if (path.equals("/admin/realms/weave/clients")) {
            respond(exchange, 200, "[{\"id\":\"client-uuid\",\"clientId\":\"weave-app\"}]");
        } else if (path.equals("/admin/realms/weave/clients/client-uuid/roles/member")) {
            respond(exchange, 200, "{\"id\":\"role-uuid\",\"name\":\"member\"}");
        } else if (exchange.getRequestMethod().equals("POST") || exchange.getRequestMethod().equals("PUT")) {
            respond(exchange, 204, "");
        } else {
            respond(exchange, 404, "{}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        if (status == 204) {
            exchange.sendResponseHeaders(status, -1);
        } else {
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        }
        exchange.close();
    }
}
