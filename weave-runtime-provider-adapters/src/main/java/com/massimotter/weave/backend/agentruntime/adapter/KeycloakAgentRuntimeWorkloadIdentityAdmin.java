package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityAdmin;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityInventory;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Keycloak 26.7 OIDC Dynamic Client Registration anti-corruption boundary.
 *
 * <p>Creation uses the narrowly authorized runtime service account. Every subsequent operation
 * uses only the client-bound Registration Access Token held by the protected Cell SecretRef.
 * This adapter has no Keycloak Admin REST inventory or generic client-management capability.
 */
public final class KeycloakAgentRuntimeWorkloadIdentityAdmin
        implements RuntimeWorkloadIdentityAdmin, RuntimeWorkloadIdentityInventory {

    static final String CLIENT_AUTHENTICATOR_PRIVATE_KEY_JWT = "private_key_jwt";
    private static final String ASSERTION_TYPE =
            "urn:ietf:params:oauth:client-assertion-type:jwt-bearer";
    private static final Pattern CLIENT_ID =
            Pattern.compile("^weaver-cell-[A-Za-z0-9_-]+$");
    private static final PSSParameterSpec PS256 = new PSSParameterSpec(
            "SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1);

    private final Settings settings;
    private final FileRuntimeWorkloadCredentialStore credentials;
    private final KeycloakAdminAccessTokenProvider accessTokens;
    private final KeycloakClientRegistrationTransport transport;
    private final ObjectMapper mapper;
    private final Clock clock;

    public KeycloakAgentRuntimeWorkloadIdentityAdmin(
            Settings settings,
            FileRuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            KeycloakClientRegistrationTransport transport,
            ObjectMapper objectMapper) {
        this(settings, credentials, accessTokens, transport, objectMapper, Clock.systemUTC());
    }

    KeycloakAgentRuntimeWorkloadIdentityAdmin(
            Settings settings,
            FileRuntimeWorkloadCredentialStore credentials,
            KeycloakAdminAccessTokenProvider accessTokens,
            KeycloakClientRegistrationTransport transport,
            ObjectMapper objectMapper,
            Clock clock) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.accessTokens = Objects.requireNonNull(accessTokens, "accessTokens");
        this.transport = Objects.requireNonNull(transport, "transport");
        this.mapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RuntimeWorkloadBinding ensureBinding(EnsureBindingCommand command) {
        Objects.requireNonNull(command, "command");
        requireNamespace(command.clientId());
        requirePrivateKeyJwt(command.authenticationMethod());
        String owner = owner(command.organizationRef(), command.personRef(), command.cellRef(),
                command.clientId());
        RuntimeWorkloadCredentialState credential = credentials.find(command.clientId())
                .orElseGet(() -> credentials.create(
                        new com.massimotter.weave.backend.agentruntime.port
                                .RuntimeWorkloadCredentialStore.CreateCredentialCommand(
                                command.clientId(), owner, command.authenticationMethod())));
        requireCredential(credential, owner, command.authenticationMethod(),
                credential.credentialRef());

        var existing = credentials.registrationAuthority(command.clientId(), owner);
        if (existing.isEmpty()) {
            createRegistration(command, owner, credential);
        } else {
            if (!existing.orElseThrow().enabled()) {
                credential = reenable(
                        command.clientId(),
                        owner,
                        existing.orElseThrow(),
                        existing.orElseThrow().serviceAccountSubject());
                verifyWorkloadSubject(
                        command.clientId(),
                        owner,
                        credential,
                        existing.orElseThrow().serviceAccountSubject());
            } else {
                LifecycleResult current = retrieve(command.clientId(), owner, credential);
                requireSubject(existing.orElseThrow().serviceAccountSubject(), current.subject());
            }
        }
        FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority =
                credentials.registrationAuthority(command.clientId(), owner).orElseThrow();
        if (!authority.enabled()) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration authority did not become active");
        }
        return binding(command.clientId(), credential, authority.serviceAccountSubject());
    }

    @Override
    public RuntimeWorkloadBinding reconcileBinding(ReconcileBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        RuntimeWorkloadCredentialState credential = requireCredential(command.binding(), owner);
        FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority =
                credentials.registrationAuthority(command.binding().clientId(), owner)
                        .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                                "The workload registration authority is unavailable"));
        if (!authority.enabled()) {
            credential = reenable(
                    command.binding().clientId(),
                    owner,
                    authority,
                    command.binding().subject());
            verifyWorkloadSubject(
                    command.binding().clientId(),
                    owner,
                    credential,
                    command.binding().subject());
        } else {
            LifecycleResult observed = retrieve(command.binding().clientId(), owner, credential);
            requireSubject(command.binding().subject(), observed.subject());
        }
        return command.binding();
    }

    private RuntimeWorkloadCredentialState reenable(
            String clientId,
            String owner,
            FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority,
            String subject) {
        String rotationRef = "reenable:" + authority.tokenFingerprint();
        var rotation =
                new com.massimotter.weave.backend.agentruntime.port
                        .RuntimeWorkloadCredentialStore.RotateCredentialCommand(
                        clientId, owner, rotationRef);
        RuntimeWorkloadCredentialState prepared = credentials.prepareRotation(rotation);
        JsonNode replacementJwks = replacementJwks(prepared);
        ObjectNode replacementMetadata = metadata(clientId, replacementJwks, true);
        RuntimeWorkloadCredentialState activated = credentials.withRegistrationAccessToken(
                clientId,
                owner,
                (observedAuthority, token) -> {
                    JsonNode response = transport.update(
                            clientId,
                            observedAuthority.registrationUri(),
                            replacementMetadata,
                            token);
                    byte[] next = registrationAccessToken(response);
                    try {
                        RegistrationResponse registration;
                        try {
                            // A mutating response is the RAT/URI handoff. The policy-enforced
                            // final client state is read and validated after atomic persistence.
                            registration = registrationResponse(
                                    response,
                                    clientId,
                                    observedAuthority.registrationUri(),
                                    next);
                        } catch (RuntimeException responseFailure) {
                            failClosedRemoteRegistration(
                                    clientId,
                                    owner,
                                    observedAuthority.registrationUri(),
                                    next,
                                    responseFailure);
                            throw responseFailure;
                        }
                        try {
                            return credentials.activateReplacementAuthority(
                                    clientId,
                                    owner,
                                    rotationRef,
                                    observedAuthority.tokenFingerprint(),
                                    registration.registrationUri(),
                                    next,
                                    subject);
                        } catch (RuntimeException persistenceFailure) {
                            failClosedRemoteRegistration(
                                    clientId,
                                    owner,
                                    registration.registrationUri(),
                                    next,
                                    persistenceFailure);
                            throw persistenceFailure;
                        }
                    } finally {
                        Arrays.fill(next, (byte) 0);
                    }
                });
        retrieve(clientId, owner, publicJwks(activated));
        return activated;
    }

    private JsonNode replacementJwks(RuntimeWorkloadCredentialState prepared) {
        JsonNode projected = publicJwks(prepared);
        ArrayNode keys = (ArrayNode) projected.path("keys");
        ArrayNode replacement = mapper.createArrayNode();
        for (JsonNode key : keys) {
            if (!prepared.activeKeyId().equals(text(key, "kid"))) {
                replacement.add(key.deepCopy());
            }
        }
        if (replacement.size() != 1) {
            throw new RuntimeWorkloadIdentityException(
                    "The replacement workload credential is ambiguous");
        }
        return mapper.createObjectNode().set("keys", replacement);
    }

    @Override
    public void requireCurrentBinding(CurrentBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        RuntimeWorkloadCredentialState credential = requireCredential(command.binding(), owner);
        FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority =
                credentials.registrationAuthority(command.binding().clientId(), owner)
                        .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                                "The workload registration authority is unavailable"));
        if (!authority.enabled()) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration authority is disabled");
        }
        LifecycleResult observed = retrieve(command.binding().clientId(), owner, credential);
        requireSubject(command.binding().subject(), observed.subject());
    }

    @Override
    public RuntimeWorkloadBinding rotateBinding(RotateBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        RuntimeWorkloadCredentialState prepared = credentials.prepareRotation(
                new com.massimotter.weave.backend.agentruntime.port
                        .RuntimeWorkloadCredentialStore.RotateCredentialCommand(
                        command.binding().clientId(), owner, command.rotationRef()));
        update(
                command.binding().clientId(),
                owner,
                prepared,
                metadata(command.binding().clientId(), prepared, true),
                command.binding().subject(),
                true);
        RuntimeWorkloadCredentialState activated = credentials.activateRotation(
                new com.massimotter.weave.backend.agentruntime.port
                        .RuntimeWorkloadCredentialStore.RotateCredentialCommand(
                        command.binding().clientId(), owner, command.rotationRef()));
        verifyWorkloadSubject(command.binding().clientId(), owner, activated,
                command.binding().subject());
        return command.binding();
    }

    @Override
    public RuntimeWorkloadBinding retirePreviousCredential(RetireCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        var retirement = new com.massimotter.weave.backend.agentruntime.port
                .RuntimeWorkloadCredentialStore.RetireCredentialCommand(
                command.binding().clientId(), owner, command.rotationRef());
        RuntimeWorkloadCredentialState activeOnly = credentials.prepareRetirement(retirement);
        update(
                command.binding().clientId(),
                owner,
                activeOnly,
                metadata(command.binding().clientId(), activeOnly, true),
                command.binding().subject(),
                true);
        credentials.completeRetirement(retirement);
        return command.binding();
    }

    @Override
    public void disableBinding(DisableBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        RuntimeWorkloadCredentialState credential = requireCredential(command.binding(), owner);
        update(
                command.binding().clientId(),
                owner,
                credential,
                metadata(command.binding().clientId(), revocationJwks(), true),
                command.binding().subject(),
                false);
    }

    @Override
    public void deleteBinding(DeleteBindingCommand command) {
        Objects.requireNonNull(command, "command");
        String owner = requireBindingOwner(
                command.organizationRef(), command.personRef(), command.cellRef(), command.binding());
        credentials.withRegistrationAccessToken(
                command.binding().clientId(),
                owner,
                (authority, token) -> {
                    transport.delete(
                            command.binding().clientId(),
                            authority.registrationUri(),
                            token);
                    return null;
                });
        credentials.delete(
                new com.massimotter.weave.backend.agentruntime.port
                        .RuntimeWorkloadCredentialStore.DeleteCredentialCommand(
                        command.binding().clientId(), owner));
    }

    @Override
    public Snapshot scan() {
        List<ClientObservation> observations = new ArrayList<>();
        for (FileRuntimeWorkloadCredentialStore.RegistrationAuthorityEntry entry
                : credentials.registrationAuthorities()) {
            FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority =
                    entry.authority();
            observations.add(new ClientObservation(
                    authority.registrationUri().toString(),
                    entry.clientId(),
                    authority.enabled(),
                    ManagementState.MANAGED,
                    entry.ownerFingerprint(),
                    authority.organizationFingerprint(),
                    authority.personFingerprint(),
                    authority.cellFingerprint(),
                    true,
                    authority.serviceAccountSubject(),
                    "client-jwt",
                    entry.acceptedKeyIds()));
        }
        observations.sort(Comparator.comparing(ClientObservation::clientId));
        String projection = observations.stream()
                .map(value -> value.clientId() + "\u0000" + value.providerRef() + "\u0000"
                        + value.enabled() + "\u0000" + value.ownerFingerprint())
                .reduce("", (left, right) -> left + "\u0001" + right);
        return new Snapshot(fingerprint(projection), observations);
    }

    @Override
    public void quarantineManaged(QuarantineManagedCommand command) {
        Objects.requireNonNull(command, "command");
        FileRuntimeWorkloadCredentialStore.RegistrationAuthorityEntry entry =
                credentials.registrationAuthorities().stream()
                        .filter(value -> value.clientId().equals(command.clientId())
                                && value.ownerFingerprint().equals(command.ownerFingerprint())
                                && value.authority().registrationUri().toString()
                                        .equals(command.providerRef()))
                        .findFirst()
                        .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                                "The workload quarantine target is not locally owned"));
        RuntimeWorkloadCredentialState credential = credentials.find(entry.clientId())
                .orElseThrow();
        update(
                entry.clientId(),
                entry.ownerFingerprint(),
                credential,
                metadata(entry.clientId(), revocationJwks(), true),
                entry.authority().serviceAccountSubject(),
                false);
    }

    private void createRegistration(
            EnsureBindingCommand command,
            String owner,
            RuntimeWorkloadCredentialState credential) {
        String administrationToken = accessTokens.accessToken();
        JsonNode response;
        try {
            response = transport.create(
                    metadata(command.clientId(), credential, false), administrationToken);
        } catch (RuntimeException failure) {
            accessTokens.invalidate(administrationToken);
            throw failure;
        }
        byte[] token = registrationAccessToken(response);
        try {
            RegistrationResponse registration = registrationResponse(
                    response, command.clientId(), null, token);
            validateMetadata(response, command.clientId(), publicJwks(credential));
            String subject = verifyWorkloadSubject(
                    command.clientId(), owner, credential, null);
            credentials.bindRegistrationAuthority(
                    command.clientId(),
                    owner,
                    RuntimeWorkloadOwnership.fingerprint(command.organizationRef()),
                    RuntimeWorkloadOwnership.fingerprint(command.personRef()),
                    RuntimeWorkloadOwnership.fingerprint(command.cellRef()),
                    registration.registrationUri(),
                    token,
                    subject);
        } catch (RuntimeException registrationFailure) {
            failClosedRemoteRegistration(
                    command.clientId(),
                    owner,
                    expectedRegistrationUri(command.clientId()),
                    token,
                    registrationFailure);
            throw registrationFailure;
        } finally {
            Arrays.fill(token, (byte) 0);
        }
    }

    private LifecycleResult retrieve(
            String clientId,
            String owner,
            RuntimeWorkloadCredentialState credential) {
        return retrieve(clientId, owner, publicJwks(credential));
    }

    private LifecycleResult retrieve(
            String clientId,
            String owner,
            JsonNode expectedJwks) {
        return credentials.withRegistrationAccessToken(
                clientId,
                owner,
                (authority, token) -> {
                    JsonNode response = transport.retrieve(
                            clientId, authority.registrationUri(), token);
                    byte[] next = registrationAccessToken(response);
                    try {
                        RegistrationResponse registration;
                        try {
                            // Keycloak can render this response before post-update Client Policy
                            // enforcement. The retrieve below validates the durable final state.
                            registration = registrationResponse(
                                    response, clientId, authority.registrationUri(), next);
                            validateMetadata(response, clientId, expectedJwks);
                        } catch (RuntimeException responseFailure) {
                            failClosedRemoteRegistration(
                                    clientId,
                                    owner,
                                    authority.registrationUri(),
                                    next,
                                    responseFailure);
                            throw responseFailure;
                        }
                        persistRotatedAuthority(
                                clientId,
                                owner,
                                authority,
                                registration,
                                authority.serviceAccountSubject(),
                                authority.enabled());
                    } finally {
                        Arrays.fill(next, (byte) 0);
                    }
                    return new LifecycleResult(authority.serviceAccountSubject());
                });
    }

    private void update(
            String clientId,
            String owner,
            RuntimeWorkloadCredentialState credential,
            ObjectNode metadata,
            String subject,
            boolean enabled) {
        JsonNode expectedJwks = metadata.path("jwks").deepCopy();
        credentials.withRegistrationAccessToken(
                clientId,
                owner,
                (authority, token) -> {
                    JsonNode response = transport.update(
                            clientId, authority.registrationUri(), metadata, token);
                    byte[] next = registrationAccessToken(response);
                    try {
                        RegistrationResponse registration;
                        try {
                            registration = registrationResponse(
                                    response, clientId, authority.registrationUri(), next);
                        } catch (RuntimeException responseFailure) {
                            failClosedRemoteRegistration(
                                    clientId,
                                    owner,
                                    authority.registrationUri(),
                                    next,
                                    responseFailure);
                            throw responseFailure;
                        }
                        persistRotatedAuthority(
                                clientId,
                                owner,
                                authority,
                                registration,
                                subject,
                                enabled);
                    } finally {
                        Arrays.fill(next, (byte) 0);
                    }
                    return null;
                });
        retrieve(clientId, owner, expectedJwks);
    }

    private void persistRotatedAuthority(
            String clientId,
            String owner,
            FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority,
            RegistrationResponse registration,
            String subject,
            boolean enabled) {
        byte[] next = registration.registrationAccessToken();
        try {
            if (authority.tokenFingerprint().equals(
                    fingerprint(new String(next, StandardCharsets.UTF_8)))
                    && authority.registrationUri().equals(registration.registrationUri())
                    && authority.serviceAccountSubject().equals(subject)
                    && authority.enabled() == enabled) {
                return;
            }
            credentials.replaceRegistrationAuthority(
                    clientId,
                    owner,
                    authority.tokenFingerprint(),
                    registration.registrationUri(),
                    next,
                    subject,
                    enabled);
        } catch (RuntimeException persistenceFailure) {
            failClosedRemoteRegistration(
                    clientId,
                    owner,
                    registration.registrationUri(),
                    next,
                    persistenceFailure);
            throw persistenceFailure;
        } finally {
            Arrays.fill(next, (byte) 0);
        }
    }

    private void failClosedRemoteRegistration(
            String clientId,
            String owner,
            URI registrationUri,
            byte[] currentRegistrationAccessToken,
            RuntimeException primaryFailure) {
        try {
            transport.delete(clientId, registrationUri, currentRegistrationAccessToken);
        } catch (RuntimeException cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
        try {
            credentials.delete(
                    new com.massimotter.weave.backend.agentruntime.port
                            .RuntimeWorkloadCredentialStore.DeleteCredentialCommand(
                            clientId, owner));
        } catch (RuntimeException cleanupFailure) {
            primaryFailure.addSuppressed(cleanupFailure);
        }
    }

    private ObjectNode metadata(
            String clientId,
            RuntimeWorkloadCredentialState credential,
            boolean update) {
        return metadata(clientId, publicJwks(credential), update);
    }

    private JsonNode publicJwks(RuntimeWorkloadCredentialState credential) {
        try {
            return mapper.readTree(credential.publicJwks());
        } catch (JacksonException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload public JWK set is invalid");
        }
    }

    private ObjectNode metadata(String clientId, JsonNode jwks, boolean update) {
        requireNamespace(clientId);
        ObjectNode metadata = mapper.createObjectNode();
        if (update) {
            metadata.put("client_id", clientId);
        }
        metadata.put("client_name", clientId);
        metadata.put("token_endpoint_auth_method", CLIENT_AUTHENTICATOR_PRIVATE_KEY_JWT);
        metadata.put("token_endpoint_auth_signing_alg", "PS256");
        metadata.put("subject_type", "public");
        metadata.put("backchannel_logout_session_required", false);
        metadata.put("backchannel_logout_revoke_offline_tokens", false);
        metadata.put("frontchannel_logout_session_required", false);
        metadata.put("scope", String.join(" ", settings.optionalClientScopes()));
        metadata.set("redirect_uris", mapper.createArrayNode());
        metadata.set("grant_types", mapper.createArrayNode().add("client_credentials"));
        metadata.set("response_types", mapper.createArrayNode());
        metadata.set("jwks", jwks.deepCopy());
        return metadata;
    }

    private JsonNode revocationJwks() {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyPairGenerator
                    .getInstance("RSA")
                    .generateKeyPair()
                    .getPublic();
            ObjectNode key = mapper.createObjectNode();
            key.put("kty", "RSA");
            key.put("use", "sig");
            key.put("alg", "PS256");
            key.put("kid", "revoked_" + UUID.randomUUID());
            key.put("n", unsigned(publicKey.getModulus()));
            key.put("e", unsigned(publicKey.getPublicExponent()));
            return mapper.createObjectNode().set("keys", mapper.createArrayNode().add(key));
        } catch (java.security.GeneralSecurityException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "Unable to construct the workload revocation key");
        }
    }

    private byte[] registrationAccessToken(JsonNode response) {
        JsonNode value = response == null
                ? null
                : response.path("registration_access_token");
        if (value == null
                || !value.isString()
                || value.stringValue().isBlank()
                || value.stringValue().length() > 16 * 1024) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak returned an invalid Registration Access Token");
        }
        return value.stringValue().getBytes(StandardCharsets.UTF_8);
    }

    private RegistrationResponse registrationResponse(
            JsonNode response,
            String clientId,
            URI expectedUri,
            byte[] registrationAccessToken) {
        if (response == null
                || !clientId.equals(text(response, "client_id"))
                || !CLIENT_AUTHENTICATOR_PRIVATE_KEY_JWT.equals(
                        text(response, "token_endpoint_auth_method"))) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak returned an inconsistent client-registration response");
        }
        URI uri;
        try {
            uri = URI.create(text(response, "registration_client_uri"));
        } catch (IllegalArgumentException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak returned an invalid registration URI");
        }
        URI configuredUri = expectedRegistrationUri(clientId);
        if (!configuredUri.equals(uri)
                || (expectedUri != null && !expectedUri.equals(uri))) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak changed the client-bound registration URI");
        }
        return new RegistrationResponse(uri, registrationAccessToken);
    }

    private void validateMetadata(
            JsonNode response, String clientId, JsonNode expectedJwks) {
        Set<String> scopes =
                new HashSet<>(List.of(text(response, "scope").trim().split("\\s+")));
        Set<String> grants = strings(response.path("grant_types"));
        Set<String> redirects = strings(response.path("redirect_uris"));
        Set<String> responseTypes = strings(response.path("response_types"));
        if (!clientId.equals(text(response, "client_name"))
                || !new HashSet<>(settings.optionalClientScopes()).equals(scopes)
                || !Set.of("client_credentials").equals(grants)
                || !redirects.isEmpty()
                || !responseTypes.isEmpty()
                || !"public".equals(text(response, "subject_type"))
                || !"PS256".equals(text(response, "token_endpoint_auth_signing_alg"))
                || !exactPublicJwks(expectedJwks, response.path("jwks"))
                || hasForbiddenMetadata(response)) {
            throw new RuntimeWorkloadIdentityException(
                    "The Keycloak workload registration metadata has drifted");
        }
    }

    private URI expectedRegistrationUri(String clientId) {
        return URI.create(
                settings.issuer().toASCIIString()
                        + "/clients-registrations/openid-connect/"
                        + clientId);
    }

    private String verifyWorkloadSubject(
            String clientId,
            String owner,
            RuntimeWorkloadCredentialState credential,
            String expectedSubject) {
        return credentials.withActivePrivateJwk(clientId, owner, privateJwk -> {
            String assertion = clientAssertion(clientId, privateJwk);
            JsonNode token = transport.clientCredentials(Map.of(
                    "grant_type", "client_credentials",
                    "client_id", clientId,
                    "client_assertion_type", ASSERTION_TYPE,
                    "client_assertion", assertion));
            String accessToken = text(token, "access_token");
            String subject = tokenSubject(accessToken);
            if (expectedSubject != null) {
                requireSubject(expectedSubject, subject);
            }
            return subject;
        });
    }

    private String clientAssertion(String clientId, byte[] privateJwk) {
        try {
            JsonNode key = mapper.readTree(privateJwk);
            String keyId = text(key, "kid");
            ObjectNode header = mapper.createObjectNode();
            header.put("alg", "PS256");
            header.put("typ", "JWT");
            header.put("kid", keyId);
            Instant now = clock.instant();
            ObjectNode claims = mapper.createObjectNode();
            claims.put("iss", clientId);
            claims.put("sub", clientId);
            claims.put("aud", settings.issuer().toString());
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", now.plusSeconds(60).getEpochSecond());
            claims.put("jti", UUID.randomUUID().toString());
            String signingInput = base64(mapper.writeValueAsBytes(header))
                    + "." + base64(mapper.writeValueAsBytes(claims));
            Signature signer = Signature.getInstance("RSASSA-PSS");
            signer.setParameter(PS256);
            signer.initSign(KeyFactory.getInstance("RSA").generatePrivate(
                    new RSAPrivateCrtKeySpec(
                            integer(key, "n"),
                            integer(key, "e"),
                            integer(key, "d"),
                            integer(key, "p"),
                            integer(key, "q"),
                            integer(key, "dp"),
                            integer(key, "dq"),
                            integer(key, "qi"))));
            signer.update(signingInput.getBytes(StandardCharsets.US_ASCII));
            return signingInput + "." + base64(signer.sign());
        } catch (Exception failure) {
            throw new RuntimeWorkloadIdentityException(
                    "Unable to authenticate the workload with private_key_jwt");
        }
    }

    private String tokenSubject(String accessToken) {
        try {
            String[] segments = accessToken.split("\\.");
            if (segments.length != 3) {
                throw new IllegalArgumentException();
            }
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(segments[1]));
            return text(claims, "sub");
        } catch (RuntimeException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak returned a malformed workload access token");
        }
    }

    private RuntimeWorkloadCredentialState requireCredential(
            RuntimeWorkloadBinding binding, String owner) {
        RuntimeWorkloadCredentialState credential = credentials.find(binding.clientId())
                .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                        "The workload credential SecretRef is unavailable"));
        requireCredential(
                credential, owner, binding.authenticationMethod(), binding.credentialRef());
        return credential;
    }

    private static void requireCredential(
            RuntimeWorkloadCredentialState credential,
            String owner,
            RuntimeWorkloadBinding.AuthenticationMethod method,
            String credentialRef) {
        if (!owner.equals(credential.ownerFingerprint())
                || method != credential.authenticationMethod()
                || !credentialRef.equals(credential.credentialRef())) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload credential is not bound to the requested Cell");
        }
    }

    private String requireBindingOwner(
            String organizationRef,
            String personRef,
            String cellRef,
            RuntimeWorkloadBinding binding) {
        requireNamespace(binding.clientId());
        requirePrivateKeyJwt(binding.authenticationMethod());
        String owner = owner(organizationRef, personRef, cellRef, binding.clientId());
        FileRuntimeWorkloadCredentialStore.RegistrationAuthority authority =
                credentials.registrationAuthority(binding.clientId(), owner)
                        .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                                "The workload registration authority is unavailable"));
        requireSubject(binding.subject(), authority.serviceAccountSubject());
        return owner;
    }

    private RuntimeWorkloadBinding binding(
            String clientId,
            RuntimeWorkloadCredentialState credential,
            String subject) {
        return new RuntimeWorkloadBinding(
                settings.issuer().toString(),
                subject,
                clientId,
                RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT,
                credential.credentialRef());
    }

    private static String owner(
            String organizationRef, String personRef, String cellRef, String clientId) {
        return RuntimeWorkloadOwnership.ownerFingerprint(
                organizationRef, personRef, cellRef, clientId);
    }

    private static void requireNamespace(String clientId) {
        if (clientId == null || !CLIENT_ID.matcher(clientId).matches()) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload client id is outside the reserved Cell namespace");
        }
    }

    private static void requirePrivateKeyJwt(
            RuntimeWorkloadBinding.AuthenticationMethod method) {
        if (method != RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload client must use private_key_jwt");
        }
    }

    private static void requireSubject(String expected, String observed) {
        if (!Objects.equals(expected, observed) || expected == null || expected.isBlank()) {
            throw new RuntimeWorkloadIdentityException(
                    "The immutable workload service-account subject changed");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isString() || value.stringValue().isBlank()) {
            throw new RuntimeWorkloadIdentityException(
                    "Keycloak returned an incomplete client-registration response");
        }
        return value.stringValue();
    }

    private static Set<String> strings(JsonNode value) {
        if (!(value instanceof ArrayNode array)) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : array) {
            if (!item.isString() || item.stringValue().isBlank()) {
                return Set.of();
            }
            values.add(item.stringValue());
        }
        return Set.copyOf(values);
    }

    private static Set<String> keyIds(JsonNode jwks) {
        JsonNode keys = jwks == null ? null : jwks.get("keys");
        if (!(keys instanceof ArrayNode array) || array.isEmpty()) {
            return Set.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode key : array) {
            JsonNode kid = key.get("kid");
            if (kid == null || !kid.isString() || kid.stringValue().isBlank()) {
                return Set.of();
            }
            values.add(kid.stringValue());
        }
        return Set.copyOf(values);
    }

    private static boolean exactPublicJwks(JsonNode expected, JsonNode observed) {
        if (expected == null
                || observed == null
                || !expected.equals(observed)
                || keyIds(expected).isEmpty()) {
            return false;
        }
        JsonNode keys = observed.get("keys");
        if (!(keys instanceof ArrayNode array)) {
            return false;
        }
        for (JsonNode key : array) {
            if (!key.isObject()
                    || key.size() != 6
                    || !"RSA".equals(text(key, "kty"))
                    || !"sig".equals(text(key, "use"))
                    || !"PS256".equals(text(key, "alg"))
                    || !key.hasNonNull("kid")
                    || !key.hasNonNull("n")
                    || !key.hasNonNull("e")) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasForbiddenMetadata(JsonNode response) {
        for (String field : List.of(
                "client_secret",
                "client_secret_expires_at",
                "jwks_uri",
                "sector_identifier_uri",
                "software_id",
                "software_version",
                "software_statement",
                "client_uri",
                "logo_uri",
                "policy_uri",
                "tos_uri",
                "initiate_login_uri",
                "root_url",
                "base_url",
                "admin_url",
                "provider_url",
                "web_origins",
                "request_uris",
                "protocol_mappers",
                "protocolMappers",
                "attributes")) {
            JsonNode value = response.get(field);
            if (value != null
                    && !value.isNull()
                    && !(value.isString() && value.stringValue().isBlank())
                    && !(value.isArray() && value.isEmpty())
                    && !(value.isObject() && value.isEmpty())) {
                return true;
            }
        }
        return false;
    }

    private static BigInteger integer(JsonNode jwk, String field) {
        return new BigInteger(1, Base64.getUrlDecoder().decode(text(jwk, field)));
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return base64(bytes);
    }

    private static String base64(byte[] value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }

    private static String fingerprint(String value) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    public record Settings(
            URI adminBaseUrl,
            URI issuer,
            String realm,
            Duration timeout,
            String workloadRole,
            List<String> defaultClientScopes,
            List<String> optionalClientScopes,
            int accessTokenLifespanSeconds) {
        public Settings {
            requireHttp(adminBaseUrl, "adminBaseUrl", false);
            requireHttp(issuer, "issuer", true);
            if (realm == null || realm.isBlank() || realm.contains("/")) {
                throw new IllegalArgumentException("realm is required");
            }
            String encodedRealm =
                    URLEncoder.encode(realm, StandardCharsets.UTF_8).replace("+", "%20");
            if (!("/realms/" + encodedRealm).equals(issuer.getRawPath())) {
                throw new IllegalArgumentException(
                        "issuer must identify the configured realm exactly");
            }
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalArgumentException("timeout must be positive");
            }
            if (!"weaver-runtime".equals(workloadRole)) {
                throw new IllegalArgumentException("workloadRole must be weaver-runtime");
            }
            defaultClientScopes =
                    defaultClientScopes == null ? List.of() : List.copyOf(defaultClientScopes);
            if (!Set.of("weaver-runtime-workload").equals(
                    new HashSet<>(defaultClientScopes))) {
                throw new IllegalArgumentException(
                        "defaultClientScopes must contain only weaver-runtime-workload");
            }
            optionalClientScopes =
                    optionalClientScopes == null ? List.of() : List.copyOf(optionalClientScopes);
            if (!Set.of("agent-runtime.profile.read", "mcp.tools", "files.read")
                    .equals(new HashSet<>(optionalClientScopes))) {
                throw new IllegalArgumentException(
                        "optionalClientScopes must contain only the approved workload scopes");
            }
            if (accessTokenLifespanSeconds < 5 || accessTokenLifespanSeconds > 300) {
                throw new IllegalArgumentException(
                        "workload access-token lifespan must be between 5 and 300 seconds");
            }
        }

        URI tokenEndpoint() {
            String encodedRealm =
                    URLEncoder.encode(realm, StandardCharsets.UTF_8).replace("+", "%20");
            return adminBaseUrl.resolve(
                    "/realms/" + encodedRealm + "/protocol/openid-connect/token");
        }

        private static void requireHttp(URI uri, String field, boolean httpsOnly) {
            if (uri == null
                    || uri.getHost() == null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || !("https".equalsIgnoreCase(uri.getScheme())
                            || (!httpsOnly && "http".equalsIgnoreCase(uri.getScheme())))) {
                throw new IllegalArgumentException(
                        field + " must be an absolute "
                                + (httpsOnly ? "HTTPS" : "HTTP(S)") + " URI");
            }
        }
    }

    private record RegistrationResponse(
            URI registrationUri, byte[] registrationAccessToken) {}

    private record LifecycleResult(String subject) {}
}
