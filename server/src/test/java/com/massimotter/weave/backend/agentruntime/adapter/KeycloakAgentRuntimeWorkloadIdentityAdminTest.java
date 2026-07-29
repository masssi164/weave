package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.DeleteBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.DisableBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.EnsureBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.ReconcileBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.RetireCredentialCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin.RotateBindingCommand;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KeycloakAgentRuntimeWorkloadIdentityAdminTest {
    private static final String CLIENT_ID = "weaver-cell-example_01";
    private static final String ISSUER = "https://auth.weave.test/realms/weave";
    private static final String ORGANIZATION = "org:example";
    private static final String PERSON = "person:example";
    private static final String CELL = "cell:example";
    private static final String SUBJECT = "service-account-subject";

    @TempDir
    Path temporary;

    private ObjectMapper mapper;
    private FakeRegistrationTransport transport;
    private FileRuntimeWorkloadCredentialStore credentials;
    private KeycloakAgentRuntimeWorkloadIdentityAdmin adapter;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        transport = new FakeRegistrationTransport(mapper);
        credentials = new FileRuntimeWorkloadCredentialStore(temporary, mapper);
        adapter = new KeycloakAgentRuntimeWorkloadIdentityAdmin(
                new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                        URI.create("http://keycloak.test"),
                        URI.create(ISSUER),
                        "weave",
                        Duration.ofSeconds(2),
                        "weaver-runtime",
                        List.of("weaver-runtime-workload"),
                        List.of("agent-runtime.profile.read", "mcp.tools", "files.read"),
                        60),
                credentials,
                () -> "runtime-admin-access-token",
                transport,
                mapper,
                Clock.fixed(Instant.parse("2026-07-29T18:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsThroughAuthenticatedDcrAndKeepsRatInsideTheProtectedCellSecretRef() throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());

        assertThat(binding.issuer()).isEqualTo(ISSUER);
        assertThat(binding.subject()).isEqualTo(SUBJECT);
        assertThat(binding.clientId()).isEqualTo(CLIENT_ID);
        assertThat(binding.authenticationMethod())
                .isEqualTo(RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
        assertThat(transport.creates).hasValue(1);
        assertThat(transport.lastAdministrationToken)
                .isEqualTo("runtime-admin-access-token");
        assertThat(transport.metadata.path("client_id").isMissingNode()).isTrue();
        assertThat(transport.metadata.path("client_name").asText()).isEqualTo(CLIENT_ID);
        assertThat(transport.metadata.path("token_endpoint_auth_method").asText())
                .isEqualTo("private_key_jwt");
        assertThat(transport.metadata.path("backchannel_logout_session_required").asBoolean())
                .isFalse();
        assertThat(transport.metadata.path("backchannel_logout_revoke_offline_tokens").asBoolean())
                .isFalse();
        assertThat(transport.metadata.path("frontchannel_logout_session_required").asBoolean())
                .isFalse();
        assertThat(transport.metadata.path("redirect_uris")).isEmpty();
        assertThat(transport.metadata.path("grant_types").get(0).asText())
                .isEqualTo("client_credentials");
        assertThat(transport.lastClientCredentials)
                .containsEntry("client_id", CLIENT_ID)
                .containsEntry("grant_type", "client_credentials")
                .containsKey("client_assertion")
                .doesNotContainKey("client_secret");
        String assertion = transport.lastClientCredentials.get("client_assertion");
        JsonNode assertionClaims = mapper.readTree(
                Base64.getUrlDecoder().decode(assertion.split("\\.")[1]));
        assertThat(assertionClaims.path("aud").asText()).isEqualTo(ISSUER);

        Path protectedRef = temporary.resolve(
                "weave/agent-runtime/cells/" + CLIENT_ID);
        assertThat(Files.isRegularFile(protectedRef)).isTrue();
        if (Files.getFileStore(protectedRef).supportsFileAttributeView("posix")) {
            assertThat(Files.getPosixFilePermissions(protectedRef))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
        }
        assertThat(credentials.find(CLIENT_ID).orElseThrow().credentialRef())
                .isEqualTo(binding.credentialRef());
        assertThat(adapter.scan().clients().getFirst().serviceAccountSubject())
                .isEqualTo(SUBJECT);
    }

    @Test
    void repeatedReadRotatesTheRatAndRejectsTheStaleCellToken() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        byte[] stale = transport.currentRat.clone();

        adapter.requireCurrentBinding(new com.massimotter.weave.backend.agentruntime.port
                .RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:verify"));
        adapter.requireCurrentBinding(new com.massimotter.weave.backend.agentruntime.port
                .RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:verify-again"));

        assertThat(transport.retrieves).hasValue(2);
        assertThatThrownBy(() -> transport.retrieve(transport.registrationUri, stale))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageNotContaining(new String(stale, StandardCharsets.UTF_8));
    }

    @Test
    void rotatesAndRetiresWorkloadKeysThroughRatAuthenticatedUpdates() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        String initialKey = credentials.find(CLIENT_ID).orElseThrow().activeKeyId();
        RotateBindingCommand rotate = new RotateBindingCommand(
                ORGANIZATION,
                PERSON,
                CELL,
                binding,
                "rotation:0000000000000001",
                "audit:rotate");

        assertThat(adapter.rotateBinding(rotate)).isEqualTo(binding);
        var overlap = credentials.find(CLIENT_ID).orElseThrow();
        assertThat(overlap.activeKeyId()).isNotEqualTo(initialKey);
        assertThat(overlap.acceptedKeyIds()).hasSize(2);
        assertThat(transport.metadata.path("jwks").path("keys")).hasSize(2);

        RetireCredentialCommand retire = new RetireCredentialCommand(
                ORGANIZATION,
                PERSON,
                CELL,
                binding,
                "rotation:0000000000000001",
                "audit:retire");
        assertThat(adapter.retirePreviousCredential(retire)).isEqualTo(binding);
        assertThat(credentials.find(CLIENT_ID).orElseThrow().acceptedKeyIds()).hasSize(1);
        assertThat(transport.metadata.path("jwks").path("keys")).hasSize(1);
        assertThat(transport.updates).hasValue(2);
    }

    @Test
    void logicalDisableRevokesThePublishedKeyAndReconcileRestoresTheSameSubject() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:disable"));

        assertThat(adapter.scan().clients().getFirst().enabled()).isFalse();
        assertThat(transport.metadata.path("jwks").path("keys").get(0).path("kid").asText())
                .startsWith("revoked_");
        assertThat(credentials.find(CLIENT_ID)).isPresent();
        assertThatThrownBy(() -> adapter.requireCurrentBinding(
                new com.massimotter.weave.backend.agentruntime.port
                        .RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                        ORGANIZATION, PERSON, CELL, binding, "audit:disabled")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("disabled");

        assertThat(adapter.reconcileBinding(new ReconcileBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:reconcile")))
                .isEqualTo(binding);
        assertThat(adapter.scan().clients().getFirst().enabled()).isTrue();
        assertThat(adapter.scan().clients().getFirst().serviceAccountSubject())
                .isEqualTo(SUBJECT);
    }

    @Test
    void crossCellAuthorityCannotReadOrDeleteTheBinding() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        assertThatThrownBy(() -> adapter.reconcileBinding(new ReconcileBindingCommand(
                ORGANIZATION, PERSON, "cell:other", binding, "audit:cross-cell")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class);
        assertThatThrownBy(() -> adapter.deleteBinding(new DeleteBindingCommand(
                ORGANIZATION, PERSON, "cell:other", binding, "audit:cross-cell-delete")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class);
        assertThat(transport.deleted).isFalse();
    }

    @Test
    void owningCellDeletesThroughItsRatAndRemovesTheProtectedRef() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        adapter.deleteBinding(new DeleteBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:delete"));

        assertThat(transport.deleted).isTrue();
        assertThat(credentials.find(CLIENT_ID)).isEmpty();
        assertThat(adapter.scan().clients()).isEmpty();
    }

    private static EnsureBindingCommand ensure() {
        return new EnsureBindingCommand(
                ORGANIZATION,
                PERSON,
                CELL,
                CLIENT_ID,
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                "audit:provision");
    }

    private static final class FakeRegistrationTransport
            implements KeycloakClientRegistrationTransport {
        private final ObjectMapper mapper;
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger creates = new AtomicInteger();
        private final AtomicInteger retrieves = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final URI registrationUri =
                URI.create("http://keycloak.test/realms/weave/clients-registrations/"
                        + "openid-connect/" + CLIENT_ID);
        private ObjectNode metadata;
        private byte[] currentRat;
        private String lastAdministrationToken;
        private Map<String, String> lastClientCredentials = Map.of();
        private boolean deleted;

        FakeRegistrationTransport(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public JsonNode create(JsonNode requested, String administrationAccessToken) {
            if (metadata != null) {
                throw new RuntimeWorkloadIdentityException("duplicate registration");
            }
            creates.incrementAndGet();
            lastAdministrationToken = administrationAccessToken;
            metadata = ((ObjectNode) requested).deepCopy();
            return response(rotateRat());
        }

        @Override
        public JsonNode retrieve(URI uri, byte[] rat) {
            requireAuthority(uri, rat);
            retrieves.incrementAndGet();
            return response(rotateRat());
        }

        @Override
        public JsonNode update(URI uri, JsonNode requested, byte[] rat) {
            requireAuthority(uri, rat);
            updates.incrementAndGet();
            metadata = ((ObjectNode) requested).deepCopy();
            return response(rotateRat());
        }

        @Override
        public void delete(URI uri, byte[] rat) {
            requireAuthority(uri, rat);
            deleted = true;
            metadata = null;
            currentRat = null;
        }

        @Override
        public JsonNode clientCredentials(Map<String, String> parameters) {
            lastClientCredentials = Map.copyOf(parameters);
            ObjectNode claims = mapper.createObjectNode().put("sub", SUBJECT);
            String payload;
            try {
                payload = Base64.getUrlEncoder().withoutPadding()
                        .encodeToString(mapper.writeValueAsBytes(claims));
            } catch (RuntimeException failure) {
                throw failure;
            }
            return mapper.createObjectNode().put("access_token", "e30." + payload + ".signature");
        }

        private ObjectNode response(byte[] rat) {
            ObjectNode response = metadata.deepCopy();
            response.put("client_id", CLIENT_ID);
            response.put("client_name", CLIENT_ID);
            response.put("registration_client_uri", registrationUri.toString());
            response.put(
                    "registration_access_token",
                    new String(rat, StandardCharsets.UTF_8));
            response.put(
                    "scope",
                    "weaver-runtime-workload agent-runtime.profile.read mcp.tools files.read");
            return response;
        }

        private byte[] rotateRat() {
            currentRat = ("rat-" + sequence.incrementAndGet())
                    .getBytes(StandardCharsets.UTF_8);
            return currentRat;
        }

        private void requireAuthority(URI uri, byte[] rat) {
            if (!registrationUri.equals(uri)
                    || currentRat == null
                    || !java.security.MessageDigest.isEqual(currentRat, rat)) {
                throw new RuntimeWorkloadIdentityException(
                        "registration authority rejected");
            }
        }
    }
}
