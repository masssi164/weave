package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.DeleteBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.DisableBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.EnsureBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.RetireCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.RotateBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakAgentRuntimeWorkloadIdentityAdminTest {
    private static final String CLIENT_ID = "weaver-cell-example_01";
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String ORGANIZATION = "org:example";
    private static final String PERSON = "person:example";
    private static final String CELL = "cell:example";

    @TempDir
    Path temporary;

    private ObjectMapper mapper;
    private FakeKeycloak keycloak;
    private FileRuntimeWorkloadCredentialStore credentials;
    private KeycloakAgentRuntimeWorkloadIdentityAdmin adapter;

    @BeforeEach
    void setUp() throws Exception {
        mapper = new ObjectMapper();
        keycloak = new FakeKeycloak(mapper);
        credentials = new FileRuntimeWorkloadCredentialStore(temporary, mapper);
        adapter = new KeycloakAgentRuntimeWorkloadIdentityAdmin(
                new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                        keycloak.baseUri(), URI.create(ISSUER), "weave", Duration.ofSeconds(2),
                        "weaver-runtime", List.of("agent-runtime.profile.read"), 60),
                credentials,
                () -> "admin-token",
                mapper);
    }

    @AfterEach
    void tearDown() {
        keycloak.close();
    }

    @Test
    void ensureCreatesAndReconcilesOneExactSecretSafeServiceAccountBinding() throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());

        assertThat(binding.issuer()).isEqualTo(ISSUER);
        assertThat(binding.subject()).isEqualTo(FakeKeycloak.SERVICE_SUBJECT);
        assertThat(binding.clientId()).isEqualTo(CLIENT_ID);
        assertThat(binding.authenticationMethod())
                .isEqualTo(RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
        assertThat(binding.credentialRef()).isEqualTo(
                "credentialref://weave/agent-runtime/cells/" + CLIENT_ID);

        ObjectNode client = keycloak.client();
        assertThat(client.path("publicClient").asBoolean()).isFalse();
        assertThat(client.path("serviceAccountsEnabled").asBoolean()).isTrue();
        assertThat(client.path("standardFlowEnabled").asBoolean()).isFalse();
        assertThat(client.path("directAccessGrantsEnabled").asBoolean()).isFalse();
        assertThat(client.path("fullScopeAllowed").asBoolean()).isFalse();
        assertThat(client.path("clientAuthenticatorType").asText()).isEqualTo("client-jwt");
        assertThat(client.path("attributes").path("weave.arc.managed").asText())
                .isEqualTo("agent-runtime-control");
        assertThat(client.path("attributes").path("keycloak.provider-owned-default").asText())
                .isEqualTo("preserved");
        JsonNode keySet = mapper.readTree(client.path("attributes").path("jwks.string").asText());
        assertThat(keySet.path("keys")).hasSize(1);
        assertThat(keySet.path("keys").get(0).has("d")).isFalse();
        assertThat(keycloak.defaultScopeNames()).isEmpty();
        assertThat(keycloak.optionalScopeNames()).containsExactly("agent-runtime.profile.read");
        assertThat(keycloak.serviceRealmRoleNames()).containsExactly("weaver-runtime");
        assertThat(keycloak.serviceClientMappings().size()).isZero();
        assertThat(keycloak.clientRealmScopeRoleNames()).containsExactly("weaver-runtime");
        assertThat(keycloak.protocolMappers()).hasSize(1);
        assertThat(keycloak.protocolMappers().get(0).path("config").path("claim.name").asText())
                .isEqualTo("client_id");

        int firstMutationCount = keycloak.mutationCount();
        assertThat(firstMutationCount).isPositive();
        keycloak.resetMutationCount();
        assertThat(adapter.ensureBinding(ensure())).isEqualTo(binding);
        assertThat(keycloak.mutationCount()).isZero();
    }

    @Test
    void anUnownedClientInTheReservedNamespaceIsNeverAdoptedOrGivenASecret() {
        keycloak.installUnownedClient(CLIENT_ID);

        assertThatThrownBy(() -> adapter.ensureBinding(ensure()))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("unowned, ambiguous, or cross-bound");
        assertThat(credentials.find(CLIENT_ID)).isEmpty();
        assertThat(keycloak.client().path("attributes").path("weave.arc.managed").isMissingNode()).isTrue();
    }

    @Test
    void privateKeyRotationUsesPublishedOverlapAndConvergesOnReplay() throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        RuntimeWorkloadCredentialState initial = credentials.find(CLIENT_ID).orElseThrow();
        keycloak.resetMutationCount();
        RotateBindingCommand rotate = new RotateBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "rotation:0000000000000001", "audit:rotate");

        assertThat(adapter.rotateBinding(rotate)).isEqualTo(binding);
        RuntimeWorkloadCredentialState overlap = credentials.find(CLIENT_ID).orElseThrow();
        assertThat(overlap.activeKeyId()).isNotEqualTo(initial.activeKeyId());
        assertThat(overlap.acceptedKeyIds()).hasSize(2);
        assertThat(mapper.readTree(keycloak.currentJwks()).path("keys")).hasSize(2);
        assertThat(keycloak.mutationCount()).isPositive();

        keycloak.resetMutationCount();
        assertThat(adapter.rotateBinding(rotate)).isEqualTo(binding);
        assertThat(keycloak.mutationCount()).isZero();

        keycloak.resetMutationCount();
        RetireCredentialCommand retire = new RetireCredentialCommand(
                ORGANIZATION, PERSON, CELL, binding, "rotation:0000000000000001", "audit:retire");
        assertThat(adapter.retirePreviousCredential(retire)).isEqualTo(binding);
        assertThat(credentials.find(CLIENT_ID).orElseThrow().acceptedKeyIds())
                .containsExactly(overlap.activeKeyId());
        assertThat(mapper.readTree(keycloak.currentJwks()).path("keys")).hasSize(1);
        assertThat(keycloak.mutationCount()).isPositive();

        keycloak.resetMutationCount();
        assertThat(adapter.retirePreviousCredential(retire)).isEqualTo(binding);
        assertThat(keycloak.mutationCount()).isZero();
    }

    @Test
    void disableAndDeleteRemoveCredentialBeforeConvergingIdempotently() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        DisableBindingCommand disable = new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:disable");

        keycloak.resetMutationCount();
        adapter.disableBinding(disable);
        assertThat(keycloak.client().path("enabled").asBoolean()).isFalse();
        assertThat(credentials.find(CLIENT_ID)).isEmpty();
        assertThat(keycloak.mutationCount()).isEqualTo(1);

        keycloak.resetMutationCount();
        adapter.disableBinding(disable);
        assertThat(keycloak.mutationCount()).isZero();

        DeleteBindingCommand delete = new DeleteBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:delete");
        adapter.deleteBinding(delete);
        assertThat(keycloak.clientExists()).isFalse();
        keycloak.resetMutationCount();
        adapter.deleteBinding(delete);
        assertThat(keycloak.mutationCount()).isZero();
    }

    private static EnsureBindingCommand ensure() {
        return new EnsureBindingCommand(
                ORGANIZATION, PERSON, CELL, CLIENT_ID,
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                "audit:provision");
    }

    private static final class FakeKeycloak implements AutoCloseable {
        private static final String CLIENT_UUID = "client-uuid";
        private static final String SERVICE_SUBJECT = "service-account-subject";
        private static final ObjectNode WORKLOAD_ROLE = role("role-weaver-runtime", "weaver-runtime");
        private static final ObjectNode EXTRA_ROLE = role("role-extra", "default-roles-weave");
        private static final ObjectNode EXTRA_CLIENT_ROLE = role("client-role-extra", "manage-realm");

        private final ObjectMapper mapper;
        private final HttpServer server;
        private final AtomicInteger mutations = new AtomicInteger();
        private final Map<String, Scope> scopes = new LinkedHashMap<>();
        private ObjectNode client;
        private final List<ObjectNode> protocolMappers = new ArrayList<>();
        private final List<String> defaultScopeIds = new ArrayList<>();
        private final List<String> optionalScopeIds = new ArrayList<>();
        private final List<ObjectNode> serviceRealmRoles = new ArrayList<>();
        private final Map<String, List<ObjectNode>> serviceClientMappings = new LinkedHashMap<>();
        private final List<ObjectNode> clientRealmScopeRoles = new ArrayList<>();

        FakeKeycloak(ObjectMapper mapper) throws IOException {
            this.mapper = mapper;
            scopes.put("scope-arc", new Scope("scope-arc", "agent-runtime.profile.read"));
            scopes.put("scope-default", new Scope("scope-default", "profile"));
            scopes.put("scope-extra", new Scope("scope-extra", "offline_access"));
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", this::handle);
            server.start();
        }

        URI baseUri() {
            return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
        }

        ObjectNode client() {
            return client == null ? mapper.createObjectNode() : client.deepCopy();
        }

        boolean clientExists() {
            return client != null;
        }

        int mutationCount() {
            return mutations.get();
        }

        void resetMutationCount() {
            mutations.set(0);
        }

        String currentJwks() {
            return client.path("attributes").path("jwks.string").asText();
        }

        List<String> defaultScopeNames() {
            return defaultScopeIds.stream().map(id -> scopes.get(id).name()).toList();
        }

        List<String> optionalScopeNames() {
            return optionalScopeIds.stream().map(id -> scopes.get(id).name()).toList();
        }

        List<String> serviceRealmRoleNames() {
            return serviceRealmRoles.stream().map(role -> role.path("name").asText()).toList();
        }

        Map<String, List<ObjectNode>> serviceClientMappings() {
            return serviceClientMappings;
        }

        List<String> clientRealmScopeRoleNames() {
            return clientRealmScopeRoles.stream().map(role -> role.path("name").asText()).toList();
        }

        List<ObjectNode> protocolMappers() {
            return protocolMappers.stream().map(ObjectNode::deepCopy).toList();
        }

        void installUnownedClient(String clientId) {
            client = mapper.createObjectNode();
            client.put("id", CLIENT_UUID);
            client.put("clientId", clientId);
            client.put("enabled", true);
            client.put("serviceAccountsEnabled", true);
            client.set("attributes", mapper.createObjectNode());
        }

        private void handle(HttpExchange exchange) throws IOException {
            try {
                String method = exchange.getRequestMethod();
                String path = URLDecoder.decode(exchange.getRequestURI().getPath(), StandardCharsets.UTF_8);
                String relative = path.substring("/admin/realms/weave".length());
                JsonNode body = body(exchange);
                if ("/clients".equals(relative)) {
                    clients(exchange, method, body);
                    return;
                }
                if (relative.startsWith("/clients/" + CLIENT_UUID)) {
                    clientResource(exchange, method, relative.substring(("/clients/" + CLIENT_UUID).length()), body);
                    return;
                }
                if ("/client-scopes".equals(relative) && "GET".equals(method)) {
                    ArrayNode result = mapper.createArrayNode();
                    scopes.values().forEach(scope -> result.add(scope.node(mapper)));
                    respond(exchange, 200, result);
                    return;
                }
                if ("/roles/weaver-runtime".equals(relative) && "GET".equals(method)) {
                    respond(exchange, 200, WORKLOAD_ROLE);
                    return;
                }
                if (relative.startsWith("/users/" + SERVICE_SUBJECT + "/role-mappings")) {
                    userRoleMappings(exchange, method,
                            relative.substring(("/users/" + SERVICE_SUBJECT + "/role-mappings").length()), body);
                    return;
                }
                respond(exchange, 404, null);
            } catch (RuntimeException exception) {
                respond(exchange, 500, mapper.createObjectNode().put("error", "fake-provider-error"));
            }
        }

        private void clients(HttpExchange exchange, String method, JsonNode body) throws IOException {
            if ("GET".equals(method)) {
                ArrayNode result = mapper.createArrayNode();
                if (client != null) {
                    result.add(mapper.createObjectNode()
                            .put("id", CLIENT_UUID)
                            .put("clientId", client.path("clientId").asText()));
                }
                respond(exchange, 200, result);
                return;
            }
            if ("POST".equals(method)) {
                mutate();
                if (client != null) {
                    respond(exchange, 409, null);
                    return;
                }
                replaceClient((ObjectNode) body);
                defaultScopeIds.clear();
                defaultScopeIds.add("scope-default");
                optionalScopeIds.clear();
                optionalScopeIds.add("scope-extra");
                serviceRealmRoles.clear();
                serviceRealmRoles.add(EXTRA_ROLE.deepCopy());
                serviceClientMappings.clear();
                serviceClientMappings.put("other-client", new ArrayList<>(List.of(EXTRA_CLIENT_ROLE.deepCopy())));
                clientRealmScopeRoles.clear();
                clientRealmScopeRoles.add(EXTRA_ROLE.deepCopy());
                respond(exchange, 201, null);
                return;
            }
            respond(exchange, 405, null);
        }

        private void clientResource(
                HttpExchange exchange,
                String method,
                String suffix,
                JsonNode body) throws IOException {
            if (suffix.isEmpty()) {
                if ("GET".equals(method)) {
                    respond(exchange, client == null ? 404 : 200, client);
                } else if ("PUT".equals(method)) {
                    mutate();
                    replaceClient((ObjectNode) body);
                    respond(exchange, 204, null);
                } else if ("DELETE".equals(method)) {
                    mutate();
                    client = null;
                    respond(exchange, 204, null);
                } else {
                    respond(exchange, 405, null);
                }
                return;
            }
            if ("/service-account-user".equals(suffix) && "GET".equals(method)) {
                respond(exchange, 200, mapper.createObjectNode().put("id", SERVICE_SUBJECT));
                return;
            }
            if (suffix.startsWith("/protocol-mappers/models")) {
                protocolMappers(exchange, method, suffix.substring("/protocol-mappers/models".length()), body);
                return;
            }
            if (suffix.startsWith("/default-client-scopes")) {
                scopeMapping(exchange, method, suffix.substring("/default-client-scopes".length()),
                        defaultScopeIds, body);
                return;
            }
            if (suffix.startsWith("/optional-client-scopes")) {
                scopeMapping(exchange, method, suffix.substring("/optional-client-scopes".length()),
                        optionalScopeIds, body);
                return;
            }
            if ("/scope-mappings/realm".equals(suffix)) {
                roleList(exchange, method, body, clientRealmScopeRoles);
                return;
            }
            respond(exchange, 404, null);
        }

        private void protocolMappers(
                HttpExchange exchange,
                String method,
                String suffix,
                JsonNode body) throws IOException {
            if (suffix.isEmpty() && "GET".equals(method)) {
                ArrayNode result = mapper.createArrayNode();
                protocolMappers.forEach(result::add);
                respond(exchange, 200, result);
                return;
            }
            if (suffix.isEmpty() && "POST".equals(method)) {
                mutate();
                ObjectNode created = ((ObjectNode) body).deepCopy();
                created.put("id", "mapper-" + (protocolMappers.size() + 1));
                protocolMappers.add(created);
                respond(exchange, 201, null);
                return;
            }
            String id = suffix.startsWith("/") ? suffix.substring(1) : suffix;
            if ("DELETE".equals(method)) {
                mutate();
                protocolMappers.removeIf(mapper -> id.equals(mapper.path("id").asText()));
                respond(exchange, 204, null);
                return;
            }
            if ("PUT".equals(method)) {
                mutate();
                protocolMappers.removeIf(mapper -> id.equals(mapper.path("id").asText()));
                ObjectNode updated = ((ObjectNode) body).deepCopy();
                updated.put("id", id);
                protocolMappers.add(updated);
                respond(exchange, 204, null);
                return;
            }
            respond(exchange, 405, null);
        }

        private void scopeMapping(
                HttpExchange exchange,
                String method,
                String suffix,
                List<String> mapped,
                JsonNode body) throws IOException {
            if (suffix.isEmpty() && "GET".equals(method)) {
                ArrayNode result = mapper.createArrayNode();
                mapped.forEach(id -> result.add(scopes.get(id).node(mapper)));
                respond(exchange, 200, result);
                return;
            }
            String id = suffix.startsWith("/") ? suffix.substring(1) : suffix;
            if ("DELETE".equals(method)) {
                mutate();
                mapped.remove(id);
                respond(exchange, 204, null);
                return;
            }
            if ("PUT".equals(method)) {
                mutate();
                if (!mapped.contains(id)) {
                    mapped.add(id);
                }
                respond(exchange, 204, null);
                return;
            }
            respond(exchange, 405, null);
        }

        private void userRoleMappings(
                HttpExchange exchange,
                String method,
                String suffix,
                JsonNode body) throws IOException {
            if (suffix.isEmpty() && "GET".equals(method)) {
                ObjectNode result = mapper.createObjectNode();
                ArrayNode realm = mapper.createArrayNode();
                serviceRealmRoles.forEach(realm::add);
                result.set("realmMappings", realm);
                ObjectNode clients = mapper.createObjectNode();
                serviceClientMappings.forEach((id, roles) -> {
                    ObjectNode mapping = mapper.createObjectNode().put("id", id);
                    ArrayNode roleNodes = mapper.createArrayNode();
                    roles.forEach(roleNodes::add);
                    mapping.set("mappings", roleNodes);
                    clients.set(id, mapping);
                });
                result.set("clientMappings", clients);
                respond(exchange, 200, result);
                return;
            }
            if ("/realm".equals(suffix)) {
                roleList(exchange, method, body, serviceRealmRoles);
                return;
            }
            if (suffix.startsWith("/clients/") && "DELETE".equals(method)) {
                mutate();
                serviceClientMappings.remove(suffix.substring("/clients/".length()));
                respond(exchange, 204, null);
                return;
            }
            respond(exchange, 404, null);
        }

        private void roleList(
                HttpExchange exchange,
                String method,
                JsonNode body,
                List<ObjectNode> roles) throws IOException {
            if ("GET".equals(method)) {
                ArrayNode result = mapper.createArrayNode();
                roles.forEach(result::add);
                respond(exchange, 200, result);
                return;
            }
            if ("DELETE".equals(method)) {
                mutate();
                for (JsonNode removed : body) {
                    String name = removed.path("name").asText();
                    roles.removeIf(role -> name.equals(role.path("name").asText()));
                }
                respond(exchange, 204, null);
                return;
            }
            if ("POST".equals(method)) {
                mutate();
                for (JsonNode added : body) {
                    String name = added.path("name").asText();
                    if (roles.stream().noneMatch(role -> name.equals(role.path("name").asText()))) {
                        roles.add(((ObjectNode) added).deepCopy());
                    }
                }
                respond(exchange, 204, null);
                return;
            }
            respond(exchange, 405, null);
        }

        private void replaceClient(ObjectNode next) {
            client = next.deepCopy();
            client.put("id", CLIENT_UUID);
            ((ObjectNode) client.path("attributes"))
                    .put("keycloak.provider-owned-default", "preserved");
            JsonNode suppliedMappers = client.path("protocolMappers");
            if (suppliedMappers.isArray()) {
                protocolMappers.clear();
                int index = 1;
                for (JsonNode supplied : suppliedMappers) {
                    ObjectNode copy = ((ObjectNode) supplied).deepCopy();
                    copy.put("id", "mapper-" + index++);
                    protocolMappers.add(copy);
                }
            }
        }

        private JsonNode body(HttpExchange exchange) throws IOException {
            byte[] bytes = exchange.getRequestBody().readAllBytes();
            return bytes.length == 0 ? mapper.nullNode() : mapper.readTree(bytes);
        }

        private void mutate() {
            mutations.incrementAndGet();
        }

        private void respond(HttpExchange exchange, int status, JsonNode body) throws IOException {
            byte[] bytes = body == null ? new byte[0] : mapper.writeValueAsBytes(body);
            if (bytes.length > 0) {
                exchange.getResponseHeaders().set("Content-Type", "application/json");
            }
            exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        }

        private static ObjectNode role(String id, String name) {
            return new ObjectMapper().createObjectNode().put("id", id).put("name", name);
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private record Scope(String id, String name) {
            ObjectNode node(ObjectMapper mapper) {
                return mapper.createObjectNode().put("id", id).put("name", name);
            }
        }
    }
}
