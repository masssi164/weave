package com.massimotter.weave.backend.agentruntime.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory.QuarantineManagedCommand;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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
                        KeycloakAgentRuntimeWorkloadIdentityAdmin
                                .WORKLOAD_ACCESS_TOKEN_LIFESPAN_SECONDS),
                credentials,
                () -> "runtime-admin-access-token",
                transport,
                mapper,
                Clock.fixed(Instant.parse("2026-07-29T18:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsAConfiguredLifespanThatCanCrossTheSixtySecondJwtBoundary() {
        assertThatThrownBy(() -> new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                        URI.create("http://keycloak.test"),
                        URI.create(ISSUER),
                        "weave",
                        Duration.ofSeconds(2),
                        "weaver-runtime",
                        List.of("weaver-runtime-workload"),
                        List.of("agent-runtime.profile.read", "mcp.tools", "files.read"),
                        60))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("workload access-token lifespan must be exactly 59 seconds");
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
    void derivesTheVersionedKeycloakProviderStateDigestContract() {
        ObjectNode publicKey = mapper.createObjectNode();
        publicKey.put("kty", "RSA");
        publicKey.put("use", "sig");
        publicKey.put("alg", "PS256");
        publicKey.put("kid", "contract-key-01");
        publicKey.put("n", "public-modulus");
        publicKey.put("e", "AQAB");
        JsonNode publicJwks =
                mapper.createObjectNode().set("keys", mapper.createArrayNode().add(publicKey));

        String digest = KeycloakAgentRuntimeWorkloadIdentityAdmin.intendedStateDigest(
                new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                        URI.create("http://keycloak.test"),
                        URI.create(ISSUER),
                        "weave",
                        Duration.ofSeconds(2),
                        "weaver-runtime",
                        List.of("weaver-runtime-workload"),
                        List.of("agent-runtime.profile.read", "mcp.tools", "files.read"),
                        KeycloakAgentRuntimeWorkloadIdentityAdmin
                                .WORKLOAD_ACCESS_TOKEN_LIFESPAN_SECONDS),
                mapper,
                CLIENT_ID,
                publicJwks,
                FileRuntimeWorkloadCredentialStore.RegistrationHandoffOperation.CREATE);

        assertThat(digest)
                .isEqualTo(
                        "sha256:c3defc7bd4d3b064ae000f316aec6d5bcae0ba1e12ab4bac8f21c26300b583ee");
    }

    @Test
    void acceptsTheBoundedKeycloakDefaultAndAccountRoleProjection() {
        transport.nextTokenClaimsMutation = claims -> {
            claims.withObject("realm_access")
                    .withArray("roles")
                    .add("default-roles-weave")
                    .add("offline_access")
                    .add("uma_authorization");
            claims.withObject("resource_access")
                    .withObject("account")
                    .putArray("roles")
                    .add("manage-account")
                    .add("manage-account-links")
                    .add("view-profile");
        };

        assertThat(adapter.ensureBinding(ensure()).subject()).isEqualTo(SUBJECT);
    }

    @Test
    void classifiesAMissingWorkloadRoleWithoutDisclosingTokenClaims() {
        transport.nextTokenClaimsMutation = claims ->
                claims.withObject("realm_access").withArray("roles").removeAll();

        assertThatThrownBy(() -> adapter.ensureBinding(ensure()))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining(
                        "malformed workload access token [constraint=realm-role-required]")
                .hasMessageNotContaining("realm_access");
    }

    @Test
    void classifiesAnUnexpectedRealmRoleWithoutDisclosingItsName() {
        transport.nextTokenClaimsMutation = claims -> claims
                .withObject("realm_access")
                .withArray("roles")
                .add("same-name-or-extra-role");

        assertThatThrownBy(() -> adapter.ensureBinding(ensure()))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining(
                        "malformed workload access token [constraint=realm-role-unexpected]")
                .hasMessageNotContaining("same-name-or-extra-role");
    }

    @Test
    void rejectsAWorkloadTokenWithAnUnknownClientRoleProjection() {
        transport.nextTokenClaimsMutation = claims -> claims
                .withObject("resource_access")
                .withObject("other-client")
                .putArray("roles")
                .add("same-name-or-extra-role");

        assertThatThrownBy(() -> adapter.ensureBinding(ensure()))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining(
                        "malformed workload access token [constraint=client-roles]")
                .hasMessageNotContaining("same-name-or-extra-role");
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner())).isPresent();
    }

    @Test
    void repeatedReadRetainsTheRatUntilAMutatingLifecycleOperationRotatesIt() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        byte[] initial = transport.currentRat.clone();

        adapter.requireCurrentBinding(new com.massimotter.weave.backend.agentruntime.port
                .RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:verify"));
        adapter.requireCurrentBinding(new com.massimotter.weave.backend.agentruntime.port
                .RuntimeWorkloadBindingAuthority.CurrentBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:verify-again"));

        assertThat(transport.retrieves).hasValue(3);
        assertThat(transport.currentRat).isEqualTo(initial);

        adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:disable-for-rat-rotation"));

        assertThatThrownBy(() -> transport.retrieve(CLIENT_ID, transport.registrationUri, initial))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageNotContaining(new String(initial, StandardCharsets.UTF_8));
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
    void logicalDisableRevokesThePublishedKeyAndReconcileUsesANewKeyForTheSameSubject() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        String retiredKey = credentials.find(CLIENT_ID).orElseThrow().activeKeyId();
        adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:disable"));

        assertThat(transport.lastUpdateResponseHadScope).isFalse();
        assertThat(adapter.scan().clients().getFirst().enabled()).isFalse();
        assertThat(transport.metadata.path("client_id").asText()).isEqualTo(CLIENT_ID);
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
        var replacement = credentials.find(CLIENT_ID).orElseThrow();
        assertThat(replacement.activeKeyId()).isNotEqualTo(retiredKey);
        assertThat(replacement.acceptedKeyIds()).containsExactly(replacement.activeKeyId());
        assertThat(transport.metadata.path("jwks").path("keys")).hasSize(1);
        assertThat(transport.metadata.path("jwks").path("keys").get(0).path("kid").asText())
                .isEqualTo(replacement.activeKeyId());
    }

    @Test
    void failedPostUpdateVerificationRetainsOnlyTheRotatedRatForRecovery() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        transport.failNextRetrieve = true;

        assertThatThrownBy(() -> adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:disable")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("simulated post-update verification failure");

        assertThat(adapter.reconcileBinding(new ReconcileBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:recover")))
                .isEqualTo(binding);
        assertThat(transport.updates).hasValue(2);
        assertThat(adapter.scan().clients().getFirst().enabled()).isTrue();
    }

    @Test
    void failedRatPersistenceLeavesTheDurableHandoffForRecovery() throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        Path protectedRef = temporary.resolve(
                "weave/agent-runtime/cells/" + CLIENT_ID);
        transport.afterNextUpdate = () -> {
            try {
                Files.delete(protectedRef);
                Files.createDirectory(protectedRef);
            } catch (Exception failure) {
                throw new IllegalStateException("unable to install persistence fault", failure);
            }
        };

        RuntimeWorkloadIdentityException failure = catchThrowableOfType(
                RuntimeWorkloadIdentityException.class,
                () -> adapter.disableBinding(new DisableBindingCommand(
                        ORGANIZATION,
                        PERSON,
                        CELL,
                        binding,
                        "audit:persistence-failure")));

        assertThat(failure)
                .isNotNull()
                .hasMessageContaining("regular non-symlink file");
        assertThat(transport.deleted).isFalse();
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner()))
                .hasValueSatisfying(handoff ->
                        assertThat(handoff.phase())
                                .isEqualTo(FileRuntimeWorkloadCredentialStore
                                        .RegistrationHandoffPhase.STAGED));
    }

    @Test
    void failedRatPersistenceRemainsRestartRecoverableWithoutDeletingTheClient()
            throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        Path protectedRef = temporary.resolve(
                "weave/agent-runtime/cells/" + CLIENT_ID);
        byte[] previousEnvelope = Files.readAllBytes(protectedRef);
        transport.afterNextUpdate = () -> {
            try {
                Files.delete(protectedRef);
                Files.createDirectory(protectedRef);
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "unable to install persistence fault", failure);
            }
        };

        RuntimeWorkloadIdentityException failure = catchThrowableOfType(
                RuntimeWorkloadIdentityException.class,
                () -> adapter.disableBinding(new DisableBindingCommand(
                        ORGANIZATION,
                        PERSON,
                        CELL,
                        binding,
                        "audit:persistence-and-compensation-failure")));

        assertThat(failure)
                .isNotNull()
                .hasMessageNotContaining("rat-");
        assertThat(transport.deleted).isFalse();
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner()))
                .hasValueSatisfying(handoff ->
                        assertThat(handoff.phase())
                                .isEqualTo(FileRuntimeWorkloadCredentialStore
                                        .RegistrationHandoffPhase.STAGED));

        Files.delete(protectedRef);
        Files.write(protectedRef, previousEnvelope);
        if (Files.getFileStore(protectedRef).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    protectedRef, PosixFilePermissions.fromString("rw-------"));
        }
        FileRuntimeWorkloadCredentialStore restartedCredentials =
                new FileRuntimeWorkloadCredentialStore(temporary, mapper);
        KeycloakAgentRuntimeWorkloadIdentityAdmin restarted =
                adapter(restartedCredentials);

        RuntimeWorkloadBinding replacement = restarted.ensureBinding(ensure());

        assertThat(replacement.clientId()).isEqualTo(CLIENT_ID);
        assertThat(transport.deleteAttempts).hasValue(0);
        assertThat(transport.creates).hasValue(1);
        assertThat(restartedCredentials.registrationHandoff(CLIENT_ID, owner()))
                .isEmpty();
        assertThat(restartedCredentials.find(CLIENT_ID)).isPresent();
        Arrays.fill(previousEnvelope, (byte) 0);
    }

    @Test
    void localDeleteFailureIsRetriedIdempotentlyAfterRestart() throws Exception {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        Path protectedRef = temporary.resolve(
                "weave/agent-runtime/cells/" + CLIENT_ID);
        byte[] previousEnvelope = Files.readAllBytes(protectedRef);
        transport.afterNextDelete = () -> {
            try {
                Files.delete(protectedRef);
                Files.createDirectory(protectedRef);
            } catch (Exception failure) {
                throw new IllegalStateException(
                        "unable to install delete persistence fault", failure);
            }
        };

        assertThatThrownBy(() -> adapter.deleteBinding(new DeleteBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:delete-local-failure")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageNotContaining("rat-");

        assertThat(transport.deleted).isTrue();
        assertThat(credentials.registrationDeletionIntent(CLIENT_ID, owner())).isPresent();
        Files.delete(protectedRef);
        Files.write(protectedRef, previousEnvelope);
        if (Files.getFileStore(protectedRef).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(
                    protectedRef, PosixFilePermissions.fromString("rw-------"));
        }
        FileRuntimeWorkloadCredentialStore restartedCredentials =
                new FileRuntimeWorkloadCredentialStore(temporary, mapper);
        KeycloakAgentRuntimeWorkloadIdentityAdmin restarted =
                adapter(restartedCredentials);

        restarted.deleteBinding(new DeleteBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:delete-retry"));

        assertThat(transport.deleteAttempts).hasValue(2);
        assertThat(restartedCredentials.registrationDeletionIntent(CLIENT_ID, owner()))
                .isEmpty();
        assertThat(restartedCredentials.find(CLIENT_ID)).isEmpty();
        Arrays.fill(previousEnvelope, (byte) 0);
    }

    @Test
    void mutatingResponseWithoutRatRotationIsQuarantinedWithoutFallback() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        transport.reuseRatOnNextUpdate = true;

        assertThatThrownBy(() -> adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION, PERSON, CELL, binding, "audit:missing-rat-rotation")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("did not rotate")
                .hasMessageNotContaining("rat-");

        assertThat(transport.deleted).isFalse();
        assertThat(credentials.find(CLIENT_ID)).isPresent();
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner())).isPresent();
    }

    @Test
    void inconsistentCreateResponseLeavesARecoverablePreparedAuthority() {
        transport.nextResponseMutation = response -> response.put(
                "registration_client_uri",
                "https://foreign.example/realms/weave/clients-registrations/"
                        + "openid-connect/" + CLIENT_ID);

        assertThatThrownBy(() -> adapter.ensureBinding(ensure()))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("registration URI")
                .hasMessageNotContaining("rat-");

        assertThat(transport.deleted).isFalse();
        assertThat(credentials.find(CLIENT_ID)).isPresent();
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner())).isPresent();
        assertThat(adapter.ensureBinding(ensure()).clientId()).isEqualTo(CLIENT_ID);
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner())).isEmpty();
    }

    @Test
    void driftedFinalStateAfterUpdateLeavesTheExactHandoffForRepair() {
        RuntimeWorkloadBinding binding = adapter.ensureBinding(ensure());
        transport.nextRetrieveMutation =
                response -> response.put("scope", "mcp.tools realm-management");

        assertThatThrownBy(() -> adapter.disableBinding(new DisableBindingCommand(
                ORGANIZATION,
                PERSON,
                CELL,
                binding,
                "audit:metadata-drift")))
                .isInstanceOf(RuntimeWorkloadIdentityException.class)
                .hasMessageContaining("metadata has drifted")
                .hasMessageNotContaining("rat-");

        assertThat(transport.deleted).isFalse();
        assertThat(credentials.find(CLIENT_ID)).isPresent();
        assertThat(credentials.registrationHandoff(CLIENT_ID, owner())).isPresent();
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
    void quarantineUsesAnUpdateBoundToTheExactManagedClient() {
        adapter.ensureBinding(ensure());
        var observed = adapter.scan().clients().getFirst();

        adapter.quarantineManaged(new QuarantineManagedCommand(
                observed.providerRef(),
                observed.clientId(),
                observed.ownerFingerprint(),
                "audit:quarantine"));

        assertThat(transport.metadata.path("client_id").asText()).isEqualTo(CLIENT_ID);
        assertThat(adapter.scan().clients().getFirst().enabled()).isFalse();
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

    private KeycloakAgentRuntimeWorkloadIdentityAdmin adapter(
            FileRuntimeWorkloadCredentialStore credentialStore) {
        return new KeycloakAgentRuntimeWorkloadIdentityAdmin(
                new KeycloakAgentRuntimeWorkloadIdentityAdmin.Settings(
                        URI.create("http://keycloak.test"),
                        URI.create(ISSUER),
                        "weave",
                        Duration.ofSeconds(2),
                        "weaver-runtime",
                        List.of("weaver-runtime-workload"),
                        List.of("agent-runtime.profile.read", "mcp.tools", "files.read"),
                        KeycloakAgentRuntimeWorkloadIdentityAdmin
                                .WORKLOAD_ACCESS_TOKEN_LIFESPAN_SECONDS),
                credentialStore,
                () -> "runtime-admin-access-token",
                transport,
                mapper,
                Clock.fixed(
                        Instant.parse("2026-07-29T18:00:00Z"),
                        ZoneOffset.UTC));
    }

    private static String owner() {
        return com.massimotter.weave.backend.agentruntime.domain
                .RuntimeWorkloadOwnership.ownerFingerprint(
                ORGANIZATION, PERSON, CELL, CLIENT_ID);
    }

    private static final class FakeRegistrationTransport
            implements KeycloakClientRegistrationTransport {
        private final ObjectMapper mapper;
        private final AtomicInteger sequence = new AtomicInteger();
        private final AtomicInteger creates = new AtomicInteger();
        private final AtomicInteger retrieves = new AtomicInteger();
        private final AtomicInteger updates = new AtomicInteger();
        private final URI registrationUri =
                URI.create(ISSUER + "/clients-registrations/"
                        + "openid-connect/" + CLIENT_ID);
        private ObjectNode metadata;
        private byte[] currentRat;
        private String lastAdministrationToken;
        private Map<String, String> lastClientCredentials = Map.of();
        private boolean deleted;
        private boolean failNextRetrieve;
        private boolean failNextDelete;
        private boolean reuseRatOnNextUpdate;
        private boolean lastUpdateResponseHadScope;
        private Runnable afterNextUpdate = () -> {};
        private Runnable afterNextDelete = () -> {};
        private Consumer<ObjectNode> nextResponseMutation = ignored -> {};
        private Consumer<ObjectNode> nextRetrieveMutation = ignored -> {};
        private Consumer<ObjectNode> nextTokenClaimsMutation = ignored -> {};
        private final AtomicInteger deleteAttempts = new AtomicInteger();
        private RegistrationHandoffProof activeHandoff;

        FakeRegistrationTransport(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public JsonNode create(
                JsonNode requested,
                String administrationAccessToken,
                RegistrationHandoffProof handoff) {
            if (metadata != null) {
                throw new RuntimeWorkloadIdentityException("duplicate registration");
            }
            activeHandoff = copy(handoff);
            creates.incrementAndGet();
            deleted = false;
            lastAdministrationToken = administrationAccessToken;
            metadata = ((ObjectNode) requested).deepCopy();
            return response(rotateRat());
        }

        @Override
        public JsonNode retrieve(String clientId, URI uri, byte[] rat) {
            requireClient(clientId);
            requireAuthority(uri, rat);
            if (failNextRetrieve) {
                failNextRetrieve = false;
                throw new RuntimeWorkloadIdentityException(
                        "simulated post-update verification failure");
            }
            retrieves.incrementAndGet();
            ObjectNode response = response(currentRat);
            Consumer<ObjectNode> mutation = nextRetrieveMutation;
            nextRetrieveMutation = ignored -> {};
            mutation.accept(response);
            return response;
        }

        @Override
        public JsonNode update(
                String clientId,
                URI uri,
                JsonNode requested,
                byte[] rat,
                RegistrationHandoffProof handoff) {
            requireClient(clientId);
            requireAuthority(uri, rat);
            activeHandoff = copy(handoff);
            if (!CLIENT_ID.equals(requested.path("client_id").asText())) {
                throw new RuntimeWorkloadIdentityException(
                        "registration update client binding rejected");
            }
            updates.incrementAndGet();
            metadata = ((ObjectNode) requested).deepCopy();
            byte[] responseRat = currentRat;
            if (!reuseRatOnNextUpdate) {
                responseRat = rotateRat();
            }
            reuseRatOnNextUpdate = false;
            ObjectNode response = response(responseRat);
            response.remove("scope");
            lastUpdateResponseHadScope = response.has("scope");
            Runnable callback = afterNextUpdate;
            afterNextUpdate = () -> {};
            callback.run();
            return response;
        }

        @Override
        public JsonNode recover(
                String clientId,
                URI uri,
                String administrationAccessToken,
                RegistrationHandoffProof handoff) {
            requireClient(clientId);
            if (metadata == null || activeHandoff == null) {
                throw new RuntimeWorkloadIdentityException(
                        "registration handoff target unavailable");
            }
            requireProof(handoff);
            lastAdministrationToken = administrationAccessToken;
            byte[] replacement = rotateRat();
            return mapper.createObjectNode()
                    .put("client_id", CLIENT_ID)
                    .put("registration_client_uri", registrationUri.toString())
                    .put("state_digest", handoff.stateDigest())
                    .put(
                            "subject_digest",
                            digest(SUBJECT))
                    .put(
                            "registration_access_token",
                            new String(replacement, StandardCharsets.UTF_8));
        }

        @Override
        public FinalizeResult finalizeHandoff(
                String clientId,
                URI uri,
                byte[] rat,
                RegistrationHandoffProof handoff) {
            requireClient(clientId);
            requireAuthority(uri, rat);
            requireProof(handoff);
            activeHandoff = null;
            return FinalizeResult.FINALIZED;
        }

        @Override
        public void delete(String clientId, URI uri, byte[] rat) {
            requireClient(clientId);
            deleteAttempts.incrementAndGet();
            if (metadata == null && deleted) {
                return;
            }
            requireAuthority(uri, rat);
            if (failNextDelete) {
                failNextDelete = false;
                throw new RuntimeWorkloadIdentityException(
                        "simulated registration cleanup failure");
            }
            deleted = true;
            metadata = null;
            currentRat = null;
            Runnable callback = afterNextDelete;
            afterNextDelete = () -> {};
            callback.run();
        }

        @Override
        public JsonNode clientCredentials(Map<String, String> parameters) {
            lastClientCredentials = Map.copyOf(parameters);
            ObjectNode claims = mapper.createObjectNode()
                    .put("sub", SUBJECT)
                    .put("azp", CLIENT_ID);
            claims.putObject("realm_access")
                    .putArray("roles")
                    .add("weaver-runtime");
            claims.putObject("resource_access");
            Consumer<ObjectNode> mutation = nextTokenClaimsMutation;
            nextTokenClaimsMutation = ignored -> {};
            mutation.accept(claims);
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
                    "agent-runtime.profile.read mcp.tools files.read");
            Consumer<ObjectNode> mutation = nextResponseMutation;
            nextResponseMutation = ignored -> {};
            mutation.accept(response);
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

        private void requireClient(String clientId) {
            if (!CLIENT_ID.equals(clientId)) {
                throw new RuntimeWorkloadIdentityException(
                        "registration client binding rejected");
            }
        }

        private void requireProof(RegistrationHandoffProof handoff) {
            if (activeHandoff == null
                    || !activeHandoff.stateDigest().equals(handoff.stateDigest())
                    || activeHandoff.operation() != handoff.operation()
                    || !java.security.MessageDigest.isEqual(
                            activeHandoff.capability(), handoff.capability())) {
                throw new RuntimeWorkloadIdentityException(
                        "registration handoff rejected");
            }
        }

        private static String digest(String value) {
            try {
                return "sha256:"
                        + java.util.HexFormat.of().formatHex(
                                java.security.MessageDigest.getInstance("SHA-256")
                                        .digest(value.getBytes(StandardCharsets.UTF_8)));
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new IllegalStateException(impossible);
            }
        }

        private static RegistrationHandoffProof copy(
                RegistrationHandoffProof handoff) {
            return new RegistrationHandoffProof(
                    handoff.capability(), handoff.stateDigest(), handoff.operation());
        }
    }
}
