package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority.ObserveEntitlementCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthorityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonDirectory.ResolveRuntimePersonCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonNotFoundException;
import com.massimotter.weave.backend.identity.IdentityReferences;
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
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class KeycloakRuntimeEntitlementAuthorityTest {
    private static final Instant NOW = Instant.parse("2026-07-20T09:00:00Z");
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String SUBJECT = "member-uuid";

    private ObjectMapper mapper;
    private FakeKeycloak keycloak;
    private RotatingTokenProvider tokens;
    private KeycloakRuntimeEntitlementAuthority authority;

    @BeforeEach
    void setUp() throws IOException {
        mapper = new ObjectMapper();
        keycloak = new FakeKeycloak(mapper);
        tokens = new RotatingTokenProvider();
        authority = new KeycloakRuntimeEntitlementAuthority(
                settings(true), tokens, mapper, HttpClient.newHttpClient(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        keycloak.close();
    }

    @Test
    void derivesOpaqueShortLivedEvidenceFromTheEnabledUsersCurrentGroupMembership() {
        keycloak.groups.add(group("group-1", "team-a", "/weave-weaver-runtime/team-a"));
        keycloak.groups.add(group("group-2", "unrelated", "/unrelated"));

        var observation = authority.observe(command(ISSUER));

        assertThat(observation.sourceProvider()).isEqualTo("keycloak");
        assertThat(observation.sourceGroupRef()).matches("sha256:[a-f0-9]{64}");
        assertThat(observation.capabilityRevision()).matches("sha256:[a-f0-9]{64}");
        assertThat(observation.sourceGroupRef()).doesNotContain("group-1", "weave-weaver-runtime");
        assertThat(observation.observedAt()).isEqualTo(NOW);
        assertThat(observation.expiresAt()).isEqualTo(NOW.plusSeconds(300));
        assertThat(keycloak.lastAuthorization).isEqualTo("Bearer admin-token-1");
    }

    @Test
    void resolvesOnlyTheOpaqueAccountReferenceInsideTheConfiguredOrganization() {
        String personRef = IdentityReferences.accountId(ISSUER, SUBJECT);

        var resolved = authority.resolve(new ResolveRuntimePersonCommand(
                "org:example", personRef, "audit:person-resolution"));

        assertThat(resolved.organizationRef()).isEqualTo("org:example");
        assertThat(resolved.personRef()).isEqualTo(personRef);
        assertThat(resolved.memberBinding()).isEqualTo(new RuntimeMemberBinding(ISSUER, SUBJECT));
        assertThatThrownBy(() -> authority.resolve(new ResolveRuntimePersonCommand(
                "org:another", personRef, "audit:cross-org")))
                .isInstanceOf(RuntimePersonNotFoundException.class);
        assertThatThrownBy(() -> authority.resolve(new ResolveRuntimePersonCommand(
                "org:example", SUBJECT, "audit:raw-subject")))
                .isInstanceOf(RuntimePersonNotFoundException.class);

        keycloak.organizationMember = false;
        assertThatThrownBy(() -> authority.resolve(new ResolveRuntimePersonCommand(
                "org:example", personRef, "audit:removed-member")))
                .isInstanceOf(RuntimePersonNotFoundException.class);
    }

    @Test
    void disabledAbsentWrongIssuerAndNonMemberIdentitiesFailClosed() {
        assertThatThrownBy(() -> authority.observe(command(ISSUER)))
                .isInstanceOf(RuntimeEntitlementDeniedException.class)
                .hasMessageContaining("no current Weaver entitlement");

        keycloak.groups.add(group("group-1", "weave-weaver-runtime", "/weave-weaver-runtime"));
        keycloak.userEnabled = false;
        assertThatThrownBy(() -> authority.observe(command(ISSUER)))
                .isInstanceOf(RuntimeEntitlementDeniedException.class)
                .hasMessageContaining("absent or disabled");

        keycloak.userEnabled = true;
        keycloak.organizationMember = false;
        assertThatThrownBy(() -> authority.observe(command(ISSUER)))
                .isInstanceOf(RuntimeEntitlementDeniedException.class)
                .hasMessageContaining("not a current organization member");
        keycloak.organizationMember = true;

        int requests = keycloak.requests.get();
        assertThatThrownBy(() -> authority.observe(command("https://different.example/realms/weave")))
                .isInstanceOf(RuntimeEntitlementDeniedException.class)
                .hasMessageContaining("configured IDM");
        assertThat(keycloak.requests).hasValue(requests);

        KeycloakRuntimeEntitlementAuthority disabled = new KeycloakRuntimeEntitlementAuthority(
                settings(false), tokens, mapper, HttpClient.newHttpClient(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        assertThatThrownBy(() -> disabled.observe(command(ISSUER)))
                .isInstanceOf(RuntimeEntitlementDeniedException.class)
                .hasMessageContaining("disabled by policy");
    }

    @Test
    void oneRejectedAdminTokenIsInvalidatedAndRetriedWithoutLeakingProviderBodies() {
        keycloak.groups.add(group("group-1", "weave-weaver-runtime", "/weave-weaver-runtime"));
        keycloak.rejectFirstToken = true;

        assertThat(authority.observe(command(ISSUER)).sourceGroupRef()).startsWith("sha256:");
        assertThat(tokens.invalidations).hasValue(1);
        assertThat(tokens.calls).hasValue(4);

        keycloak.failureStatus = 503;
        keycloak.failureBody = "provider-secret-debug-payload";
        assertThatThrownBy(() -> authority.observe(command(ISSUER)))
                .isInstanceOf(RuntimeEntitlementAuthorityException.class)
                .hasMessage("Keycloak entitlement lookup failed with sanitized status 503")
                .hasMessageNotContaining("provider-secret");
    }

    private KeycloakRuntimeEntitlementAuthority.Settings settings(boolean enabled) {
        return new KeycloakRuntimeEntitlementAuthority.Settings(
                enabled, keycloak.baseUri(), URI.create(ISSUER), "org:example", "org-uuid", "weave",
                Duration.ofSeconds(2),
                Duration.ofMinutes(5), List.of("weave-weaver-runtime"),
                List.of("calendar.read"));
    }

    private static ObserveEntitlementCommand command(String issuer) {
        return new ObserveEntitlementCommand(
                "org:example", "person:example", new RuntimeMemberBinding(issuer, SUBJECT), "correlation:test");
    }

    private static ObjectNode group(String id, String name, String path) {
        return new ObjectMapper().createObjectNode().put("id", id).put("name", name).put("path", path);
    }

    private static final class RotatingTokenProvider implements KeycloakAdminAccessTokenProvider {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger invalidations = new AtomicInteger();
        private final AtomicInteger generation = new AtomicInteger(1);

        @Override
        public String accessToken() {
            calls.incrementAndGet();
            return "admin-token-" + generation.get();
        }

        @Override
        public void invalidate(String rejectedToken) {
            invalidations.incrementAndGet();
            generation.incrementAndGet();
        }
    }

    private static final class FakeKeycloak implements AutoCloseable {
        private final ObjectMapper mapper;
        private final HttpServer server;
        private final AtomicInteger requests = new AtomicInteger();
        private final List<ObjectNode> groups = new java.util.ArrayList<>();
        private boolean userEnabled = true;
        private boolean organizationMember = true;
        private boolean rejectFirstToken;
        private int failureStatus;
        private String failureBody = "";
        private String lastAuthorization;

        private FakeKeycloak(ObjectMapper mapper) throws IOException {
            this.mapper = mapper;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        private URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        private void handle(HttpExchange exchange) throws IOException {
            requests.incrementAndGet();
            lastAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            if (failureStatus != 0) {
                respond(exchange, failureStatus, failureBody);
                return;
            }
            if (rejectFirstToken && "Bearer admin-token-1".equals(lastAuthorization)) {
                rejectFirstToken = false;
                respond(exchange, 401, "rejected-token-body");
                return;
            }
            String path = exchange.getRequestURI().getPath();
            if (path.equals("/admin/realms/weave/organizations/org-uuid/members")) {
                ArrayNode members = mapper.createArrayNode();
                if (organizationMember) {
                    members.add(mapper.createObjectNode().put("id", SUBJECT));
                }
                respond(exchange, 200, mapper.writeValueAsString(members));
                return;
            }
            if (path.equals("/admin/realms/weave/organizations/org-uuid/members/" + SUBJECT)) {
                if (!organizationMember) {
                    respond(exchange, 404, "{}");
                    return;
                }
                respond(exchange, 200, mapper.writeValueAsString(
                        mapper.createObjectNode().put("id", SUBJECT)));
                return;
            }
            if (path.equals("/admin/realms/weave/users/" + SUBJECT)) {
                ObjectNode user = mapper.createObjectNode()
                        .put("id", SUBJECT)
                        .put("enabled", userEnabled);
                respond(exchange, 200, mapper.writeValueAsString(user));
                return;
            }
            if (path.equals("/admin/realms/weave/users/" + SUBJECT + "/groups")) {
                ArrayNode result = mapper.createArrayNode();
                groups.forEach(result::add);
                respond(exchange, 200, mapper.writeValueAsString(result));
                return;
            }
            respond(exchange, 404, "{}");
        }

        private static void respond(HttpExchange exchange, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
