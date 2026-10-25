package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadCredentialState.RotationPhase;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadCredentialStore;
import com.massimotter.weave.backend.agentruntime.port.RuntimeWorkloadIdentityException;
import com.massimotter.weave.backend.agentruntime.port.SecretRefAccess;
import java.io.IOException;
import java.math.BigInteger;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.RSAKeyGenParameterSpec;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;

/**
 * Permission-restricted local SecretRef adapter for the self-hosted file-key profile.
 * Private JWK values never leave this boundary through lifecycle return values.
 */
public final class FileRuntimeWorkloadCredentialStore
        implements RuntimeWorkloadCredentialStore, SecretRefAccess {

    static final String SCHEMA = "weave.workload-credential/v2";
    private static final String REGISTRATION_HANDOFF_SCHEMA =
            "weave.registration-authority-handoff/v2";
    private static final String REGISTRATION_DELETION_INTENT_SCHEMA =
            "weave.registration-deletion-intent/v1";
    static final String ALGORITHM = "PS256";
    private static final int RSA_BITS = 3072;
    private static final int LOCAL_LOCK_STRIPES = 64;
    private static final int MAX_SECRET_BYTES = 64 * 1024;
    private static final ReentrantLock[] CREDENTIAL_LOCKS = lockStripes();
    private static final ReentrantLock[] REGISTRATION_LIFECYCLE_LOCKS = lockStripes();
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> PRIVATE_JWK_FIELDS = Set.of(
            "kty", "use", "alg", "kid", "n", "e", "d", "p", "q", "dp", "dq", "qi");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

    private final Path root;
    private final ObjectMapper mapper;
    private final URI registrationIssuer;
    private final Clock clock;
    private final SecureRandom secureRandom;

    public FileRuntimeWorkloadCredentialStore(Path root, ObjectMapper objectMapper) {
        this(root, objectMapper, null, Clock.systemUTC(), new SecureRandom());
    }

    public FileRuntimeWorkloadCredentialStore(
            Path root, ObjectMapper objectMapper, URI registrationIssuer) {
        this(root, objectMapper, registrationIssuer, Clock.systemUTC(), new SecureRandom());
    }

    public FileRuntimeWorkloadCredentialStore(Path root, ObjectMapper objectMapper, Clock clock) {
        this(root, objectMapper, null, clock, new SecureRandom());
    }

    FileRuntimeWorkloadCredentialStore(
            Path root,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom) {
        this(root, objectMapper, null, clock, secureRandom);
    }

    private FileRuntimeWorkloadCredentialStore(
            Path root,
            ObjectMapper objectMapper,
            URI registrationIssuer,
            Clock clock,
            SecureRandom secureRandom) {
        if (root == null || objectMapper == null || clock == null || secureRandom == null) {
            throw new IllegalArgumentException("credential root, ObjectMapper, clock, and randomness are required");
        }
        this.root = root.toAbsolutePath().normalize();
        if (registrationIssuer != null
                && (registrationIssuer.getHost() == null
                || !"https".equalsIgnoreCase(registrationIssuer.getScheme())
                || registrationIssuer.getUserInfo() != null
                || registrationIssuer.getQuery() != null
                || registrationIssuer.getFragment() != null
                || registrationIssuer.getRawPath() == null
                || !registrationIssuer.getRawPath().matches("/realms/[A-Za-z0-9._~-]+"))) {
            throw new IllegalArgumentException(
                    "registrationIssuer must identify one exact HTTPS realm");
        }
        this.registrationIssuer = registrationIssuer;
        this.mapper = objectMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.clock = clock;
        this.secureRandom = secureRandom;
        ensureDirectory(this.root);
    }

    @Override
    public Optional<RuntimeWorkloadCredentialState> find(String clientId) {
        requireClientId(clientId);
        return locked(clientId, () -> read(clientId).map(this::projection));
    }

    @Override
    public RuntimeWorkloadCredentialState create(CreateCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        if (command.authenticationMethod() != RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT) {
            throw new RuntimeWorkloadIdentityException(
                    "The file-key workload adapter accepts private_key_jwt only; local shared-secret mode requires its explicit adapter");
        }
        return locked(command.clientId(), () -> {
            Optional<StoredCredential> current = read(command.clientId());
            if (current.isPresent()) {
                requireOwner(current.orElseThrow(), command.ownerFingerprint());
                requireMethod(current.orElseThrow(), command.authenticationMethod());
                return projection(current.orElseThrow());
            }
            StoredKey active = generateKey(KeyStatus.ACTIVE);
            String ref = credentialRef(command.clientId());
            StoredCredential created = new StoredCredential(
                    SCHEMA,
                    command.clientId(),
                    command.ownerFingerprint(),
                    command.authenticationMethod().name(),
                    ref,
                    null,
                    null,
                    null,
                    false,
                    null,
                    null,
                    null,
                    active.keyId(),
                    null,
                    null,
                    List.of(active));
            validate(created, command.clientId());
            write(created);
            return projection(created);
        });
    }

    @Override
    public RuntimeWorkloadCredentialState prepareRotation(RotateCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        return locked(command.clientId(), () -> {
            StoredCredential current = requireStored(command.clientId(), command.ownerFingerprint());
            requireMethod(current, RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT);
            String rotation = fingerprint(command.rotationRef());
            RotationPhase phase = phase(current);
            if (phase != RotationPhase.NONE) {
                requireRotation(current, rotation);
                return projection(current);
            }
            StoredKey pending = generateKey(KeyStatus.PENDING);
            List<StoredKey> keys = new ArrayList<>(current.keys());
            keys.add(pending);
            StoredCredential prepared = current.withRotation(rotation, keys);
            validate(prepared, command.clientId());
            write(prepared);
            return projection(prepared);
        });
    }

    @Override
    public RuntimeWorkloadCredentialState activateRotation(RotateCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        return locked(command.clientId(), () -> {
            StoredCredential current = requireStored(command.clientId(), command.ownerFingerprint());
            String rotation = fingerprint(command.rotationRef());
            requireRotation(current, rotation);
            if (phase(current) == RotationPhase.ACTIVE_OVERLAP) {
                return projection(current);
            }
            if (phase(current) != RotationPhase.PREPARED) {
                throw new RuntimeWorkloadIdentityException("The workload credential rotation is not prepared");
            }
            StoredKey pending = current.keys().stream()
                    .filter(key -> key.status() == KeyStatus.PENDING)
                    .findFirst()
                    .orElseThrow();
            List<StoredKey> activatedKeys = current.keys().stream()
                    .map(key -> key.keyId().equals(pending.keyId())
                            ? key.withStatus(KeyStatus.ACTIVE)
                            : key.withStatus(KeyStatus.PREVIOUS))
                    .toList();
            StoredCredential activated = new StoredCredential(
                    current.schemaVersion(), current.clientId(), current.ownerFingerprint(),
                    current.authenticationMethod(), current.credentialRef(),
                    current.registrationUri(), current.registrationAccessToken(),
                    current.serviceAccountSubject(), current.registrationEnabled(),
                    current.organizationFingerprint(), current.personFingerprint(),
                    current.cellFingerprint(), pending.keyId(),
                    rotation, current.lastCompletedRotationFingerprint(), activatedKeys);
            validate(activated, command.clientId());
            write(activated);
            return projection(activated);
        });
    }

    @Override
    public RuntimeWorkloadCredentialState prepareRetirement(RetireCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        return locked(command.clientId(), () -> {
            StoredCredential current = requireStored(command.clientId(), command.ownerFingerprint());
            String rotation = fingerprint(command.rotationRef());
            if (phase(current) == RotationPhase.NONE
                    && rotation.equals(current.lastCompletedRotationFingerprint())) {
                return projection(current);
            }
            requireRotation(current, rotation);
            if (phase(current) != RotationPhase.ACTIVE_OVERLAP) {
                throw new RuntimeWorkloadIdentityException("The workload credential overlap is not active");
            }
            StoredKey active = current.keys().stream()
                    .filter(key -> key.status() == KeyStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();
            StoredCredential activeOnly = new StoredCredential(
                    current.schemaVersion(), current.clientId(), current.ownerFingerprint(),
                    current.authenticationMethod(), current.credentialRef(),
                    current.registrationUri(), current.registrationAccessToken(),
                    current.serviceAccountSubject(), current.registrationEnabled(),
                    current.organizationFingerprint(), current.personFingerprint(),
                    current.cellFingerprint(), active.keyId(),
                    null, rotation, List.of(active));
            validate(activeOnly, command.clientId());
            return projection(activeOnly);
        });
    }

    @Override
    public RuntimeWorkloadCredentialState completeRetirement(RetireCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        return locked(command.clientId(), () -> {
            StoredCredential current = requireStored(command.clientId(), command.ownerFingerprint());
            String rotation = fingerprint(command.rotationRef());
            if (phase(current) == RotationPhase.NONE
                    && rotation.equals(current.lastCompletedRotationFingerprint())) {
                return projection(current);
            }
            requireRotation(current, rotation);
            if (phase(current) != RotationPhase.ACTIVE_OVERLAP) {
                throw new RuntimeWorkloadIdentityException("The workload credential overlap is not active");
            }
            StoredKey active = current.keys().stream()
                    .filter(key -> key.status() == KeyStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();
            StoredCredential retired = new StoredCredential(
                    current.schemaVersion(), current.clientId(), current.ownerFingerprint(),
                    current.authenticationMethod(), current.credentialRef(),
                    current.registrationUri(), current.registrationAccessToken(),
                    current.serviceAccountSubject(), current.registrationEnabled(),
                    current.organizationFingerprint(), current.personFingerprint(),
                    current.cellFingerprint(), active.keyId(),
                    null, rotation, List.of(active));
            validate(retired, command.clientId());
            write(retired);
            return projection(retired);
        });
    }

    @Override
    public void delete(DeleteCredentialCommand command) {
        Objects.requireNonNull(command, "command");
        locked(command.clientId(), () -> {
            Optional<StoredCredential> current = read(command.clientId());
            if (current.isEmpty()) {
                return null;
            }
            requireOwner(current.orElseThrow(), command.ownerFingerprint());
            Path path = pathForRef(current.orElseThrow().credentialRef());
            try {
                Files.delete(path);
                forceDirectory(path.getParent());
            } catch (IOException exception) {
                throw unavailable("Unable to delete the workload credential", exception);
            }
            return null;
        });
    }

    @Override
    public <T> T withSecret(String credentialRef, SecretOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        Path path = pathForRef(credentialRef);
        byte[] bytes = readSecretBytes(path);
        try {
            return operation.apply(bytes);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    Optional<RegistrationAuthority> registrationAuthority(
            String clientId, String ownerFingerprint) {
        requireClientId(clientId);
        return locked(clientId, () -> {
            StoredCredential stored = requireStored(clientId, ownerFingerprint);
            return authority(stored);
        });
    }

    List<RegistrationAuthorityEntry> registrationAuthorities() {
        Path directory = root.resolve("weave/agent-runtime/cells").normalize();
        if (!directory.startsWith(root)
                || !Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            List<String> clientIds = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(value -> value.matches("weaver-cell-[A-Za-z0-9_-]+"))
                    .sorted()
                    .limit(10_001)
                    .toList();
            if (clientIds.size() > 10_000) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration inventory exceeds its safe bound");
            }
            List<RegistrationAuthorityEntry> entries = new ArrayList<>();
            for (String clientId : clientIds) {
                locked(clientId, () -> {
                    StoredCredential stored = read(clientId).orElseThrow();
                    authority(stored).ifPresent(value -> entries.add(
                            new RegistrationAuthorityEntry(
                                    clientId,
                                    stored.ownerFingerprint(),
                                    stored.credentialRef(),
                                    Set.copyOf(projection(stored).acceptedKeyIds()),
                                    value)));
                    return null;
                });
            }
            return List.copyOf(entries);
        } catch (IOException failure) {
            throw unavailable(
                    "The workload registration inventory is unavailable", failure);
        }
    }

    void bindRegistrationAuthority(
            String clientId,
            String ownerFingerprint,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            URI registrationUri,
            byte[] registrationAccessToken,
            String serviceAccountSubject) {
        Objects.requireNonNull(registrationUri, "registrationUri");
        Objects.requireNonNull(registrationAccessToken, "registrationAccessToken");
        locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            if (authority(current).isPresent()) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration authority is already bound");
            }
            StoredCredential bound = withAuthority(
                    current,
                    registrationUri,
                    registrationAccessToken,
                    serviceAccountSubject,
                    true,
                    organizationFingerprint,
                    personFingerprint,
                    cellFingerprint);
            validate(bound, clientId);
            write(bound);
            return null;
        });
    }

    void replaceRegistrationAuthority(
            String clientId,
            String ownerFingerprint,
            String expectedTokenFingerprint,
            URI registrationUri,
            byte[] registrationAccessToken,
            String serviceAccountSubject,
            boolean enabled) {
        Objects.requireNonNull(registrationUri, "registrationUri");
        Objects.requireNonNull(registrationAccessToken, "registrationAccessToken");
        locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            RegistrationAuthority authority = authority(current)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration authority is unavailable"));
            if (!MessageDigest.isEqual(
                    authority.tokenFingerprint().getBytes(StandardCharsets.US_ASCII),
                    expectedTokenFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration authority changed concurrently");
            }
            StoredCredential replaced = withAuthority(
                    current,
                    registrationUri,
                    registrationAccessToken,
                    serviceAccountSubject,
                    enabled,
                    current.organizationFingerprint(),
                    current.personFingerprint(),
                    current.cellFingerprint());
            validate(replaced, clientId);
            write(replaced);
            return null;
        });
    }

    RuntimeWorkloadCredentialState activateReplacementAuthority(
            String clientId,
            String ownerFingerprint,
            String rotationRef,
            String expectedTokenFingerprint,
            URI registrationUri,
            byte[] registrationAccessToken,
            String serviceAccountSubject) {
        Objects.requireNonNull(rotationRef, "rotationRef");
        Objects.requireNonNull(registrationUri, "registrationUri");
        Objects.requireNonNull(registrationAccessToken, "registrationAccessToken");
        return locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            RegistrationAuthority authority = authority(current)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration authority is unavailable"));
            if (!MessageDigest.isEqual(
                    authority.tokenFingerprint().getBytes(StandardCharsets.US_ASCII),
                    expectedTokenFingerprint.getBytes(StandardCharsets.US_ASCII))) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration authority changed concurrently");
            }
            String rotation = fingerprint(rotationRef);
            requireRotation(current, rotation);
            if (phase(current) != RotationPhase.PREPARED) {
                throw new RuntimeWorkloadIdentityException(
                        "The replacement workload credential is not prepared");
            }
            StoredKey replacement = current.keys().stream()
                    .filter(key -> key.status() == KeyStatus.PENDING)
                    .findFirst()
                    .orElseThrow();
            StoredCredential activated = new StoredCredential(
                    current.schemaVersion(),
                    current.clientId(),
                    current.ownerFingerprint(),
                    current.authenticationMethod(),
                    current.credentialRef(),
                    registrationUri.toString(),
                    new String(registrationAccessToken, StandardCharsets.UTF_8),
                    serviceAccountSubject,
                    true,
                    current.organizationFingerprint(),
                    current.personFingerprint(),
                    current.cellFingerprint(),
                    replacement.keyId(),
                    null,
                    rotation,
                    List.of(replacement.withStatus(KeyStatus.ACTIVE)));
            validate(activated, clientId);
            write(activated);
            return projection(activated);
        });
    }

    <T> T withRegistrationAccessToken(
            String clientId,
            String ownerFingerprint,
            RegistrationAccessTokenOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        RegistrationTokenSnapshot snapshot = locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            RegistrationAuthority authority = authority(current)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration authority is unavailable"));
            return new RegistrationTokenSnapshot(
                    authority,
                    current.registrationAccessToken().getBytes(StandardCharsets.UTF_8));
        });
        byte[] token = snapshot.registrationAccessToken();
        try {
            return operation.apply(snapshot.authority(), token);
        } finally {
            Arrays.fill(token, (byte) 0);
        }
    }

    /**
     * Holds the exact-client lock across local preparation, remote mutation or recovery,
     * finalize, and local commit. The lifecycle lock uses a distinct file from the short
     * SecretRef mutation lock so protected reads and atomic writes remain composable inside it.
     */
    <T> T withRegistrationLifecycleLock(String clientId, Callable<T> operation) {
        requireClientId(clientId);
        Objects.requireNonNull(operation, "operation");
        Path lockPath = lifecycleLockPath(clientId);
        ReentrantLock local = stripe(REGISTRATION_LIFECYCLE_LOCKS, lockPath);
        local.lock();
        try {
            ensureDirectory(lockPath.getParent());
            try (FileChannel channel = openOwnerLockFile(lockPath);
                    FileLock ignored = channel.lock()) {
                return operation.call();
            } catch (RuntimeWorkloadIdentityException failure) {
                throw failure;
            } catch (Exception failure) {
                throw unavailable(
                        "The workload registration lifecycle lock is unavailable", failure);
            }
        } finally {
            local.unlock();
        }
    }

    RegistrationHandoff prepareRegistrationHandoff(
            String clientId,
            String ownerFingerprint,
            URI registrationUri,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            String intendedStateDigest,
            String intendedPublicJwks,
            String expectedSubjectDigest,
            String currentAuthorityFingerprint,
            boolean targetEnabled,
            RegistrationHandoffOperation operation) {
        Objects.requireNonNull(registrationUri, "registrationUri");
        Objects.requireNonNull(operation, "operation");
        requireClientId(clientId);
        return locked(clientId, () -> {
            StoredCredential credential = requireStored(clientId, ownerFingerprint);
            Optional<RegistrationAuthority> authority = authority(credential);
            if (operation == RegistrationHandoffOperation.CREATE) {
                if (authority.isPresent() || currentAuthorityFingerprint != null) {
                    throw new RuntimeWorkloadIdentityException(
                            "The workload registration create handoff has an existing authority");
                }
            } else {
                RegistrationAuthority current = authority.orElseThrow(() ->
                        new RuntimeWorkloadIdentityException(
                                "The workload registration authority is unavailable"));
                if (!constantTimeEquals(
                        current.tokenFingerprint(), currentAuthorityFingerprint)) {
                    throw new RuntimeWorkloadIdentityException(
                            "The workload registration authority changed concurrently");
                }
            }
            Optional<StoredRegistrationHandoff> existing =
                    readRegistrationHandoff(clientId);
            if (existing.isPresent()) {
                StoredRegistrationHandoff observed = existing.orElseThrow();
                requireOwner(observed, ownerFingerprint);
                if (!observed.registrationUri().equals(registrationUri.toString())
                        || !observed.intendedStateDigest().equals(intendedStateDigest)
                        || !observed.intendedPublicJwks().equals(intendedPublicJwks)
                        || !observed.operation().equals(operation)
                        || !Objects.equals(
                                observed.currentAuthorityFingerprint(),
                                currentAuthorityFingerprint)
                        || observed.targetEnabled() != targetEnabled) {
                    throw new RuntimeWorkloadIdentityException(
                            "A different workload registration handoff is already pending");
                }
                return handoff(observed);
            }
            byte[] capability = new byte[32];
            secureRandom.nextBytes(capability);
            try {
                StoredRegistrationHandoff prepared = new StoredRegistrationHandoff(
                        REGISTRATION_HANDOFF_SCHEMA,
                        clientId,
                        ownerFingerprint,
                        registrationUri.toString(),
                        organizationFingerprint,
                        personFingerprint,
                        cellFingerprint,
                        intendedStateDigest,
                        intendedPublicJwks,
                        expectedSubjectDigest,
                        currentAuthorityFingerprint,
                        Base64.getUrlEncoder().withoutPadding().encodeToString(capability),
                        null,
                        null,
                        targetEnabled,
                        RegistrationHandoffPhase.PREPARED,
                        operation,
                        0,
                        clock.instant().toString());
                validate(prepared, clientId);
                writeRegistrationHandoff(prepared);
                return handoff(prepared);
            } finally {
                Arrays.fill(capability, (byte) 0);
            }
        });
    }

    RegistrationHandoff stageRegistrationHandoff(
            String clientId,
            String ownerFingerprint,
            String expectedCapabilityFingerprint,
            byte[] replacementRegistrationAccessToken,
            String observedStateDigest,
            String observedSubjectDigest) {
        Objects.requireNonNull(replacementRegistrationAccessToken, "replacementRegistrationAccessToken");
        return locked(clientId, () -> {
            StoredRegistrationHandoff current = readRegistrationHandoff(clientId)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration handoff is unavailable"));
            requireOwner(current, ownerFingerprint);
            requireCapability(current, expectedCapabilityFingerprint);
            if (!current.intendedStateDigest().equals(observedStateDigest)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff state does not match");
            }
            if (current.expectedSubjectDigest() != null
                    && observedSubjectDigest != null
                    && !constantTimeEquals(
                            current.expectedSubjectDigest(), observedSubjectDigest)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration subject changed");
            }
            String replacement =
                    new String(replacementRegistrationAccessToken, StandardCharsets.UTF_8);
            if (current.replacementRegistrationAccessToken() != null
                    && !constantTimeEquals(
                            fingerprint(current.replacementRegistrationAccessToken()),
                            fingerprint(replacement))) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff changed concurrently");
            }
            StoredRegistrationHandoff staged = new StoredRegistrationHandoff(
                    current.schemaVersion(),
                    current.clientId(),
                    current.ownerFingerprint(),
                    current.registrationUri(),
                    current.organizationFingerprint(),
                    current.personFingerprint(),
                    current.cellFingerprint(),
                    current.intendedStateDigest(),
                    current.intendedPublicJwks(),
                    current.expectedSubjectDigest(),
                    current.currentAuthorityFingerprint(),
                    current.handoffCapability(),
                    replacement,
                    observedSubjectDigest != null
                            ? observedSubjectDigest
                            : current.observedSubjectDigest(),
                    current.targetEnabled(),
                    RegistrationHandoffPhase.STAGED,
                    current.operation(),
                    current.attemptCount(),
                    current.createdAt());
            validate(staged, clientId);
            writeRegistrationHandoff(staged);
            return handoff(staged);
        });
    }

    RegistrationHandoff bindRegistrationHandoffSubject(
            String clientId,
            String ownerFingerprint,
            String expectedCapabilityFingerprint,
            String observedSubjectDigest) {
        Objects.requireNonNull(observedSubjectDigest, "observedSubjectDigest");
        return locked(clientId, () -> {
            StoredRegistrationHandoff current = readRegistrationHandoff(clientId)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration handoff is unavailable"));
            requireOwner(current, ownerFingerprint);
            requireCapability(current, expectedCapabilityFingerprint);
            if (current.phase() != RegistrationHandoffPhase.STAGED) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff has no staged authority");
            }
            if (current.expectedSubjectDigest() != null
                    && !constantTimeEquals(
                            current.expectedSubjectDigest(), observedSubjectDigest)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration subject changed");
            }
            if (current.observedSubjectDigest() != null
                    && !constantTimeEquals(
                            current.observedSubjectDigest(), observedSubjectDigest)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration recovery subject changed");
            }
            StoredRegistrationHandoff bound = new StoredRegistrationHandoff(
                    current.schemaVersion(),
                    current.clientId(),
                    current.ownerFingerprint(),
                    current.registrationUri(),
                    current.organizationFingerprint(),
                    current.personFingerprint(),
                    current.cellFingerprint(),
                    current.intendedStateDigest(),
                    current.intendedPublicJwks(),
                    current.expectedSubjectDigest(),
                    current.currentAuthorityFingerprint(),
                    current.handoffCapability(),
                    current.replacementRegistrationAccessToken(),
                    observedSubjectDigest,
                    current.targetEnabled(),
                    current.phase(),
                    current.operation(),
                    current.attemptCount(),
                    current.createdAt());
            validate(bound, clientId);
            if (!bound.equals(current)) {
                writeRegistrationHandoff(bound);
            }
            return handoff(bound);
        });
    }

    Optional<RegistrationHandoff> registrationHandoff(
            String clientId, String ownerFingerprint) {
        requireClientId(clientId);
        return locked(clientId, () -> readRegistrationHandoff(clientId)
                .map(stored -> {
                    requireOwner(stored, ownerFingerprint);
                    return handoff(stored);
                }));
    }

    List<RegistrationHandoffEntry> registrationHandoffs() {
        Path directory = handoffDirectory();
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var paths = Files.list(directory)) {
            List<String> clientIds = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.getFileName().toString())
                    .filter(value -> value.matches("weaver-cell-[A-Za-z0-9_-]+"))
                    .sorted()
                    .limit(10_001)
                    .toList();
            if (clientIds.size() > 10_000) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff inventory exceeds its safe bound");
            }
            List<RegistrationHandoffEntry> entries = new ArrayList<>();
            for (String clientId : clientIds) {
                locked(clientId, () -> {
                    StoredRegistrationHandoff stored =
                            readRegistrationHandoff(clientId).orElseThrow();
                    entries.add(new RegistrationHandoffEntry(
                            clientId, stored.ownerFingerprint(), handoff(stored)));
                    return null;
                });
            }
            return List.copyOf(entries);
        } catch (IOException failure) {
            throw unavailable(
                    "The workload registration handoff inventory is unavailable", failure);
        }
    }

    <T> T withRegistrationHandoffSecrets(
            String clientId,
            String ownerFingerprint,
            RegistrationHandoffSecretOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        RegistrationHandoffSecretSnapshot snapshot = locked(clientId, () -> {
            StoredRegistrationHandoff stored = readRegistrationHandoff(clientId)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration handoff is unavailable"));
            requireOwner(stored, ownerFingerprint);
            return new RegistrationHandoffSecretSnapshot(
                    handoff(stored),
                    Base64.getUrlDecoder().decode(stored.handoffCapability()),
                    stored.replacementRegistrationAccessToken() == null
                            ? null
                            : stored.replacementRegistrationAccessToken()
                                    .getBytes(StandardCharsets.UTF_8));
        });
        byte[] capability = snapshot.capability();
        byte[] replacement = snapshot.replacementRegistrationAccessToken();
        try {
            return operation.apply(snapshot.handoff(), capability, replacement);
        } finally {
            Arrays.fill(capability, (byte) 0);
            if (replacement != null) {
                Arrays.fill(replacement, (byte) 0);
            }
        }
    }

    boolean registrationHandoffCommitted(
            String clientId, String ownerFingerprint) {
        return locked(clientId, () -> {
            StoredRegistrationHandoff pending = readRegistrationHandoff(clientId)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration handoff is unavailable"));
            requireOwner(pending, ownerFingerprint);
            if (pending.phase() != RegistrationHandoffPhase.STAGED) {
                return false;
            }
            Optional<StoredCredential> current = read(clientId);
            if (current.isEmpty()) {
                return false;
            }
            requireOwner(current.orElseThrow(), ownerFingerprint);
            Optional<RegistrationAuthority> authority = authority(current.orElseThrow());
            return authority.isPresent()
                    && constantTimeEquals(
                            authority.orElseThrow().tokenFingerprint(),
                            fingerprint(pending.replacementRegistrationAccessToken()))
                    && authority.orElseThrow().registrationUri()
                            .equals(URI.create(pending.registrationUri()))
                    && authority.orElseThrow().enabled() == pending.targetEnabled();
        });
    }

    void recordRegistrationHandoffAttempt(
            String clientId,
            String ownerFingerprint,
            String expectedCapabilityFingerprint) {
        locked(clientId, () -> {
            StoredRegistrationHandoff current = readRegistrationHandoff(clientId)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration handoff is unavailable"));
            requireOwner(current, ownerFingerprint);
            requireCapability(current, expectedCapabilityFingerprint);
            StoredRegistrationHandoff attempted = new StoredRegistrationHandoff(
                    current.schemaVersion(),
                    current.clientId(),
                    current.ownerFingerprint(),
                    current.registrationUri(),
                    current.organizationFingerprint(),
                    current.personFingerprint(),
                    current.cellFingerprint(),
                    current.intendedStateDigest(),
                    current.intendedPublicJwks(),
                    current.expectedSubjectDigest(),
                    current.currentAuthorityFingerprint(),
                    current.handoffCapability(),
                    current.replacementRegistrationAccessToken(),
                    current.observedSubjectDigest(),
                    current.targetEnabled(),
                    current.phase(),
                    current.operation(),
                    Math.addExact(current.attemptCount(), 1),
                    current.createdAt());
            validate(attempted, clientId);
            writeRegistrationHandoff(attempted);
            return null;
        });
    }

    void clearRegistrationHandoff(
            String clientId,
            String ownerFingerprint,
            String expectedCapabilityFingerprint) {
        locked(clientId, () -> {
            Optional<StoredRegistrationHandoff> current =
                    readRegistrationHandoff(clientId);
            if (current.isEmpty()) {
                return null;
            }
            requireOwner(current.orElseThrow(), ownerFingerprint);
            requireCapability(current.orElseThrow(), expectedCapabilityFingerprint);
            deleteRegistrationHandoff(clientId);
            return null;
        });
    }

    RegistrationDeletionIntent prepareRegistrationDeletion(
            String clientId, String ownerFingerprint) {
        return locked(clientId, () -> {
            StoredCredential credential = requireStored(clientId, ownerFingerprint);
            RegistrationAuthority authority = authority(credential)
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload registration authority is unavailable"));
            StoredRegistrationDeletionIntent prepared =
                    new StoredRegistrationDeletionIntent(
                            REGISTRATION_DELETION_INTENT_SCHEMA,
                            clientId,
                            ownerFingerprint,
                            authority.registrationUri().toString(),
                            authority.tokenFingerprint(),
                            clock.instant().toString());
            validate(prepared, clientId);
            Optional<StoredRegistrationDeletionIntent> existing =
                    readRegistrationDeletionIntent(clientId);
            if (existing.isPresent()) {
                StoredRegistrationDeletionIntent observed = existing.orElseThrow();
                requireOwner(observed, ownerFingerprint);
                if (!observed.equals(prepared)
                        && (!observed.registrationUri().equals(prepared.registrationUri())
                                || !constantTimeEquals(
                                        observed.authorityFingerprint(),
                                        prepared.authorityFingerprint()))) {
                    throw new RuntimeWorkloadIdentityException(
                            "A different workload registration deletion is already pending");
                }
                return deletionIntent(observed);
            }
            writeRegistrationDeletionIntent(prepared);
            return deletionIntent(prepared);
        });
    }

    Optional<RegistrationDeletionIntent> registrationDeletionIntent(
            String clientId, String ownerFingerprint) {
        return locked(clientId, () -> readRegistrationDeletionIntent(clientId)
                .map(stored -> {
                    requireOwner(stored, ownerFingerprint);
                    return deletionIntent(stored);
                }));
    }

    void clearRegistrationDeletionIntent(
            String clientId,
            String ownerFingerprint,
            String expectedAuthorityFingerprint) {
        locked(clientId, () -> {
            Optional<StoredRegistrationDeletionIntent> current =
                    readRegistrationDeletionIntent(clientId);
            if (current.isEmpty()) {
                return null;
            }
            requireOwner(current.orElseThrow(), ownerFingerprint);
            if (!constantTimeEquals(
                    current.orElseThrow().authorityFingerprint(),
                    expectedAuthorityFingerprint)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration deletion changed concurrently");
            }
            deleteRegistrationDeletionIntent(clientId);
            return null;
        });
    }

    <T> T withActivePrivateJwk(
            String clientId,
            String ownerFingerprint,
            PrivateJwkOperation<T> operation) {
        Objects.requireNonNull(operation, "operation");
        return locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            StoredKey active = current.keys().stream()
                    .filter(key -> key.status() == KeyStatus.ACTIVE)
                    .findFirst()
                    .orElseThrow();
            byte[] encoded = mapper.writeValueAsBytes(active.privateJwk());
            try {
                return operation.apply(encoded);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        });
    }

    <T> T withPrivateJwk(
            String clientId,
            String ownerFingerprint,
            String keyId,
            PrivateJwkOperation<T> operation) {
        Objects.requireNonNull(keyId, "keyId");
        Objects.requireNonNull(operation, "operation");
        return locked(clientId, () -> {
            StoredCredential current = requireStored(clientId, ownerFingerprint);
            StoredKey selected = current.keys().stream()
                    .filter(key -> key.keyId().equals(keyId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeWorkloadIdentityException(
                            "The workload credential key is unavailable"));
            byte[] encoded = mapper.writeValueAsBytes(selected.privateJwk());
            try {
                return operation.apply(encoded);
            } finally {
                Arrays.fill(encoded, (byte) 0);
            }
        });
    }

    private StoredCredential requireStored(String clientId, String ownerFingerprint) {
        StoredCredential current = read(clientId)
                .orElseThrow(() -> new RuntimeWorkloadIdentityException("The workload credential reference is unavailable"));
        requireOwner(current, ownerFingerprint);
        return current;
    }

    private Optional<RegistrationAuthority> authority(StoredCredential stored) {
        if (stored.registrationUri() == null) {
            return Optional.empty();
        }
        return Optional.of(new RegistrationAuthority(
                URI.create(stored.registrationUri()),
                stored.serviceAccountSubject(),
                stored.registrationEnabled(),
                fingerprint(stored.registrationAccessToken()),
                stored.organizationFingerprint(),
                stored.personFingerprint(),
                stored.cellFingerprint()));
    }

    private StoredCredential withAuthority(
            StoredCredential current,
            URI registrationUri,
            byte[] registrationAccessToken,
            String serviceAccountSubject,
            boolean enabled,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint) {
        String token = new String(registrationAccessToken, StandardCharsets.UTF_8);
        return new StoredCredential(
                current.schemaVersion(),
                current.clientId(),
                current.ownerFingerprint(),
                current.authenticationMethod(),
                current.credentialRef(),
                registrationUri.toString(),
                token,
                serviceAccountSubject,
                enabled,
                organizationFingerprint,
                personFingerprint,
                cellFingerprint,
                current.activeKeyId(),
                current.rotationFingerprint(),
                current.lastCompletedRotationFingerprint(),
                current.keys());
    }

    private RegistrationHandoff handoff(StoredRegistrationHandoff stored) {
        byte[] capability = Base64.getUrlDecoder().decode(stored.handoffCapability());
        try {
            return new RegistrationHandoff(
                    URI.create(stored.registrationUri()),
                    stored.intendedStateDigest(),
                    stored.intendedPublicJwks(),
                    stored.expectedSubjectDigest(),
                    stored.currentAuthorityFingerprint(),
                    fingerprint(capability),
                    stored.replacementRegistrationAccessToken() == null
                            ? null
                            : fingerprint(stored.replacementRegistrationAccessToken()),
                    stored.observedSubjectDigest(),
                    stored.organizationFingerprint(),
                    stored.personFingerprint(),
                    stored.cellFingerprint(),
                    stored.targetEnabled(),
                    stored.phase(),
                    stored.operation(),
                    stored.attemptCount(),
                    Instant.parse(stored.createdAt()));
        } finally {
            Arrays.fill(capability, (byte) 0);
        }
    }

    private RegistrationDeletionIntent deletionIntent(
            StoredRegistrationDeletionIntent stored) {
        return new RegistrationDeletionIntent(
                URI.create(stored.registrationUri()),
                stored.authorityFingerprint(),
                Instant.parse(stored.createdAt()));
    }

    private Optional<StoredRegistrationHandoff> readRegistrationHandoff(String clientId) {
        Path path = handoffPath(clientId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        byte[] bytes = readSecretBytes(path);
        try {
            StoredRegistrationHandoff stored =
                    mapper.readValue(bytes, StoredRegistrationHandoff.class);
            validate(stored, clientId);
            return Optional.of(stored);
        } catch (RuntimeException exception) {
            throw unavailable(
                    "The workload registration handoff envelope is invalid", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private Optional<StoredRegistrationDeletionIntent> readRegistrationDeletionIntent(
            String clientId) {
        Path path = deletionIntentPath(clientId);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        byte[] bytes = readSecretBytes(path);
        try {
            StoredRegistrationDeletionIntent stored =
                    mapper.readValue(bytes, StoredRegistrationDeletionIntent.class);
            validate(stored, clientId);
            return Optional.of(stored);
        } catch (RuntimeException exception) {
            throw unavailable(
                    "The workload registration deletion intent is invalid", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private Optional<StoredCredential> read(String clientId) {
        String ref = credentialRef(clientId);
        Path path = pathForRef(ref);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        byte[] bytes = readSecretBytes(path);
        try {
            StoredCredential stored = mapper.readValue(bytes, StoredCredential.class);
            validate(stored, clientId);
            return Optional.of(stored);
        } catch (RuntimeException exception) {
            throw unavailable("The workload credential envelope is invalid", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private byte[] readSecretBytes(Path path) {
        try {
            requireSecureRegularFile(path);
            long size = Files.size(path);
            if (size < 1 || size > MAX_SECRET_BYTES) {
                throw new RuntimeWorkloadIdentityException("The SecretRef payload size is invalid");
            }
            return Files.readAllBytes(path);
        } catch (IOException exception) {
            throw unavailable("The SecretRef payload is unavailable", exception);
        }
    }

    private void write(StoredCredential stored) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(stored);
        } catch (JacksonException exception) {
            throw unavailable("Unable to encode the workload credential envelope", exception);
        }
        writeSecret(
                pathForRef(stored.credentialRef()),
                stored.clientId(),
                bytes,
                "Unable to atomically persist the workload credential");
    }

    private void writeRegistrationHandoff(StoredRegistrationHandoff stored) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(stored);
        } catch (JacksonException exception) {
            throw unavailable(
                    "Unable to encode the workload registration handoff envelope", exception);
        }
        writeSecret(
                handoffPath(stored.clientId()),
                stored.clientId(),
                bytes,
                "Unable to atomically persist the workload registration handoff");
    }

    private void writeRegistrationDeletionIntent(
            StoredRegistrationDeletionIntent stored) {
        byte[] bytes;
        try {
            bytes = mapper.writeValueAsBytes(stored);
        } catch (JacksonException exception) {
            throw unavailable(
                    "Unable to encode the workload registration deletion intent",
                    exception);
        }
        writeSecret(
                deletionIntentPath(stored.clientId()),
                stored.clientId(),
                bytes,
                "Unable to atomically persist the workload registration deletion intent");
    }

    private void writeSecret(
            Path target, String clientId, byte[] bytes, String failureMessage) {
        ensureDirectory(target.getParent());
        Path temporary = null;
        try {
            temporary =
                    createOwnerTempFile(target.getParent(), "." + clientId + "-", ".tmp");
            setFilePermissions(temporary, OWNER_FILE_PERMISSIONS);
            try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            Files.move(temporary, target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            setFilePermissions(target, OWNER_FILE_PERMISSIONS);
            forceDirectory(target.getParent());
        } catch (IOException | UnsupportedOperationException exception) {
            throw unavailable(failureMessage, exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException ignored) {
                    // The incomplete file is owner-only and never referenced by a CredentialRef.
                }
            }
        }
    }

    private void deleteRegistrationHandoff(String clientId) {
        try {
            Path path = handoffPath(clientId);
            Files.deleteIfExists(path);
            forceDirectory(path.getParent());
        } catch (IOException failure) {
            throw unavailable(
                    "Unable to remove the workload registration handoff", failure);
        }
    }

    private void deleteRegistrationDeletionIntent(String clientId) {
        try {
            Path path = deletionIntentPath(clientId);
            Files.deleteIfExists(path);
            forceDirectory(path.getParent());
        } catch (IOException failure) {
            throw unavailable(
                    "Unable to remove the workload registration deletion intent", failure);
        }
    }

    private RuntimeWorkloadCredentialState projection(StoredCredential stored) {
        // Key order is independent of activation phase so publishing a prepared set and then
        // activating its next key does not create a second, unnecessary Keycloak mutation.
        List<StoredKey> accepted = stored.keys().stream()
                .sorted(Comparator.comparing(StoredKey::keyId))
                .toList();
        StoredKey active = accepted.stream()
                .filter(key -> key.status() == KeyStatus.ACTIVE)
                .findFirst()
                .orElseThrow();
        LinkedHashSet<String> keyIds = new LinkedHashSet<>();
        ArrayNode keys = mapper.createArrayNode();
        for (StoredKey key : accepted) {
            keyIds.add(key.keyId());
            keys.add(publicJwk(key.privateJwk()));
        }
        ObjectNode jwks = mapper.createObjectNode();
        jwks.set("keys", keys);
        try {
            return new RuntimeWorkloadCredentialState(
                    stored.credentialRef(),
                    RuntimeWorkloadBinding.AuthenticationMethod.valueOf(stored.authenticationMethod()),
                    stored.ownerFingerprint(),
                    active.keyId(),
                    fingerprintForJwk(active.privateJwk()),
                    Instant.parse(active.createdAt()),
                    keyIds,
                    mapper.writeValueAsString(jwks),
                    phase(stored),
                    stored.rotationFingerprint());
        } catch (JacksonException exception) {
            throw unavailable("Unable to project the workload public key set", exception);
        }
    }

    private ObjectNode publicJwk(Map<String, String> privateJwk) {
        ObjectNode result = mapper.createObjectNode();
        for (String field : List.of("kty", "use", "alg", "kid", "n", "e")) {
            result.put(field, privateJwk.get(field));
        }
        return result;
    }

    private StoredKey generateKey(KeyStatus status) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(new RSAKeyGenParameterSpec(RSA_BITS, RSAKeyGenParameterSpec.F4), secureRandom);
            KeyPair pair = generator.generateKeyPair();
            RSAPublicKey publicKey = (RSAPublicKey) pair.getPublic();
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) pair.getPrivate();
            String keyId = keyId(publicKey);
            Map<String, String> jwk = new LinkedHashMap<>();
            jwk.put("kty", "RSA");
            jwk.put("use", "sig");
            jwk.put("alg", ALGORITHM);
            jwk.put("kid", keyId);
            jwk.put("n", unsigned(publicKey.getModulus()));
            jwk.put("e", unsigned(publicKey.getPublicExponent()));
            jwk.put("d", unsigned(privateKey.getPrivateExponent()));
            jwk.put("p", unsigned(privateKey.getPrimeP()));
            jwk.put("q", unsigned(privateKey.getPrimeQ()));
            jwk.put("dp", unsigned(privateKey.getPrimeExponentP()));
            jwk.put("dq", unsigned(privateKey.getPrimeExponentQ()));
            jwk.put("qi", unsigned(privateKey.getCrtCoefficient()));
            return new StoredKey(keyId, status, clock.instant().toString(), jwk);
        } catch (GeneralSecurityException exception) {
            throw unavailable("Unable to generate the workload private key", exception);
        }
    }

    private void validate(StoredCredential stored, String expectedClientId) {
        if (stored == null
                || !SCHEMA.equals(stored.schemaVersion())
                || !expectedClientId.equals(stored.clientId())
                || !credentialRef(expectedClientId).equals(stored.credentialRef())
                || stored.ownerFingerprint() == null
                || !stored.ownerFingerprint().matches("sha256:[a-f0-9]{64}")
                || !RuntimeWorkloadBinding.AuthenticationMethod.PRIVATE_KEY_JWT.name()
                        .equals(stored.authenticationMethod())
                || stored.keys() == null
                || stored.keys().isEmpty()
                || stored.keys().size() > 2
                || stored.activeKeyId() == null) {
            throw new RuntimeWorkloadIdentityException("The workload credential envelope is inconsistent");
        }
        boolean authorityAbsent = stored.registrationUri() == null
                && stored.registrationAccessToken() == null
                && stored.serviceAccountSubject() == null
                && stored.organizationFingerprint() == null
                && stored.personFingerprint() == null
                && stored.cellFingerprint() == null
                && !stored.registrationEnabled();
        boolean authorityPresent = validRegistrationUri(
                        stored.registrationUri(), stored.clientId())
                && stored.registrationAccessToken() != null
                && !stored.registrationAccessToken().isBlank()
                && stored.registrationAccessToken().length() <= 16 * 1024
                && stored.serviceAccountSubject() != null
                && !stored.serviceAccountSubject().isBlank()
                && validFingerprint(stored.organizationFingerprint())
                && validFingerprint(stored.personFingerprint())
                && validFingerprint(stored.cellFingerprint());
        if (!authorityAbsent && !authorityPresent) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration authority is inconsistent");
        }
        long active = stored.keys().stream().filter(key -> key.status() == KeyStatus.ACTIVE).count();
        long pending = stored.keys().stream().filter(key -> key.status() == KeyStatus.PENDING).count();
        long previous = stored.keys().stream().filter(key -> key.status() == KeyStatus.PREVIOUS).count();
        if (active != 1
                || !stored.keys().stream().anyMatch(key -> key.keyId().equals(stored.activeKeyId()))
                || (stored.keys().size() == 1 && (pending != 0 || previous != 0 || stored.rotationFingerprint() != null))
                || (stored.keys().size() == 2
                    && (stored.rotationFingerprint() == null || pending + previous != 1))) {
            throw new RuntimeWorkloadIdentityException("The workload credential rotation state is inconsistent");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (StoredKey key : stored.keys()) {
            validateKey(key);
            if (!ids.add(key.keyId())) {
                throw new RuntimeWorkloadIdentityException("The workload credential contains duplicate key ids");
            }
        }
        if (stored.rotationFingerprint() != null
                && !stored.rotationFingerprint().matches("sha256:[a-f0-9]{64}")) {
            throw new RuntimeWorkloadIdentityException("The workload credential rotation fingerprint is invalid");
        }
        if (stored.lastCompletedRotationFingerprint() != null
                && !stored.lastCompletedRotationFingerprint().matches("sha256:[a-f0-9]{64}")) {
            throw new RuntimeWorkloadIdentityException("The completed rotation fingerprint is invalid");
        }
    }

    private void validate(
            StoredRegistrationHandoff stored, String expectedClientId) {
        if (stored == null
                || !REGISTRATION_HANDOFF_SCHEMA.equals(stored.schemaVersion())
                || !expectedClientId.equals(stored.clientId())
                || !validFingerprint(stored.ownerFingerprint())
                || !validRegistrationUri(stored.registrationUri(), stored.clientId())
                || !validFingerprint(stored.organizationFingerprint())
                || !validFingerprint(stored.personFingerprint())
                || !validFingerprint(stored.cellFingerprint())
                || stored.intendedStateDigest() == null
                || !stored.intendedStateDigest().matches("sha256:[a-f0-9]{64}")
                || stored.intendedPublicJwks() == null
                || stored.intendedPublicJwks().isBlank()
                || stored.intendedPublicJwks().length() > 32 * 1024
                || stored.expectedSubjectDigest() != null
                        && !stored.expectedSubjectDigest().matches("sha256:[a-f0-9]{64}")
                || stored.currentAuthorityFingerprint() != null
                        && !stored.currentAuthorityFingerprint().matches(
                                "sha256:[a-f0-9]{64}")
                || stored.handoffCapability() == null
                || !stored.handoffCapability().matches("[A-Za-z0-9_-]{43}")
                || stored.replacementRegistrationAccessToken() != null
                        && (stored.replacementRegistrationAccessToken().isBlank()
                                || stored.replacementRegistrationAccessToken().length()
                                        > 16 * 1024)
                || stored.observedSubjectDigest() != null
                        && !stored.observedSubjectDigest().matches("sha256:[a-f0-9]{64}")
                || stored.phase() == null
                || stored.operation() == null
                || stored.attemptCount() < 0
                || stored.attemptCount() > 100_000
                || stored.createdAt() == null) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff is inconsistent");
        }
        byte[] capability;
        try {
            capability = Base64.getUrlDecoder().decode(stored.handoffCapability());
        } catch (IllegalArgumentException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff capability is invalid", failure);
        }
        try {
            if (capability.length != 32
                    || !stored.handoffCapability().equals(
                            Base64.getUrlEncoder()
                                    .withoutPadding()
                                    .encodeToString(capability))
                    || stored.phase() == RegistrationHandoffPhase.PREPARED
                            && stored.replacementRegistrationAccessToken() != null
                    || stored.phase() == RegistrationHandoffPhase.STAGED
                            && stored.replacementRegistrationAccessToken() == null
                    || stored.operation() == RegistrationHandoffOperation.CREATE
                            && stored.currentAuthorityFingerprint() != null
                    || stored.operation() != RegistrationHandoffOperation.CREATE
                            && stored.currentAuthorityFingerprint() == null) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff state is inconsistent");
            }
        } finally {
            Arrays.fill(capability, (byte) 0);
        }
        try {
            Instant.parse(stored.createdAt());
        } catch (RuntimeException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff timestamp is invalid", failure);
        }
    }

    private void validate(
            StoredRegistrationDeletionIntent stored, String expectedClientId) {
        if (stored == null
                || !REGISTRATION_DELETION_INTENT_SCHEMA.equals(stored.schemaVersion())
                || !expectedClientId.equals(stored.clientId())
                || !validFingerprint(stored.ownerFingerprint())
                || !validRegistrationUri(stored.registrationUri(), stored.clientId())
                || !validFingerprint(stored.authorityFingerprint())
                || stored.createdAt() == null) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration deletion intent is inconsistent");
        }
        try {
            Instant.parse(stored.createdAt());
        } catch (RuntimeException failure) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration deletion timestamp is invalid", failure);
        }
    }

    private boolean validRegistrationUri(String value, String clientId) {
        if (value == null) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            boolean validShape = uri.isAbsolute()
                    && uri.getHost() != null
                    && ("http".equalsIgnoreCase(uri.getScheme())
                            || "https".equalsIgnoreCase(uri.getScheme()))
                    && uri.getUserInfo() == null
                    && uri.getQuery() == null
                    && uri.getFragment() == null;
            if (!validShape || registrationIssuer == null) {
                return validShape;
            }
            return URI.create(
                            registrationIssuer.toASCIIString()
                                    + "/clients-registrations/openid-connect/"
                                    + clientId)
                    .equals(uri);
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private static boolean validFingerprint(String value) {
        return value != null && value.matches("sha256:[a-f0-9]{64}");
    }

    private void validateKey(StoredKey key) {
        if (key == null
                || key.status() == null
                || key.createdAt() == null
                || key.privateJwk() == null
                || !PRIVATE_JWK_FIELDS.equals(key.privateJwk().keySet())
                || !"RSA".equals(key.privateJwk().get("kty"))
                || !"sig".equals(key.privateJwk().get("use"))
                || !ALGORITHM.equals(key.privateJwk().get("alg"))
                || !key.keyId().equals(key.privateJwk().get("kid"))) {
            throw new RuntimeWorkloadIdentityException("The workload private JWK is invalid");
        }
        try {
            Instant.parse(key.createdAt());
            Map<String, String> jwk = key.privateJwk();
            BigInteger n = integer(jwk, "n");
            BigInteger e = integer(jwk, "e");
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(n, e));
            KeyFactory.getInstance("RSA").generatePrivate(new RSAPrivateCrtKeySpec(
                    n, e, integer(jwk, "d"), integer(jwk, "p"), integer(jwk, "q"),
                    integer(jwk, "dp"), integer(jwk, "dq"), integer(jwk, "qi")));
            if (!key.keyId().equals(keyId(publicKey))) {
                throw new RuntimeWorkloadIdentityException("The workload private JWK fingerprint is invalid");
            }
        } catch (GeneralSecurityException | RuntimeException exception) {
            if (exception instanceof RuntimeWorkloadIdentityException identityException) {
                throw identityException;
            }
            throw unavailable("The workload private JWK is invalid", exception);
        }
    }

    private static BigInteger integer(Map<String, String> jwk, String field) {
        byte[] decoded = Base64.getUrlDecoder().decode(jwk.get(field));
        if (decoded.length == 0) {
            throw new IllegalArgumentException("empty RSA parameter");
        }
        return new BigInteger(1, decoded);
    }

    private String fingerprintForJwk(Map<String, String> jwk) {
        try {
            RSAPublicKey publicKey = (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(integer(jwk, "n"), integer(jwk, "e")));
            return fingerprint(publicKey.getEncoded());
        } catch (GeneralSecurityException exception) {
            throw unavailable("Unable to fingerprint the workload public key", exception);
        }
    }

    private static String keyId(RSAPublicKey publicKey) {
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(digest(publicKey.getEncoded()));
        return "wk_" + encoded;
    }

    private static String unsigned(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String fingerprint(String value) {
        return fingerprint(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String fingerprint(byte[] value) {
        return "sha256:" + HexFormat.of().formatHex(digest(value));
    }

    private static byte[] digest(byte[] value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
        }
    }

    private static RotationPhase phase(StoredCredential stored) {
        if (stored.keys().stream().anyMatch(key -> key.status() == KeyStatus.PENDING)) {
            return RotationPhase.PREPARED;
        }
        if (stored.keys().stream().anyMatch(key -> key.status() == KeyStatus.PREVIOUS)) {
            return RotationPhase.ACTIVE_OVERLAP;
        }
        return RotationPhase.NONE;
    }

    private static void requireRotation(StoredCredential stored, String rotation) {
        if (!rotation.equals(stored.rotationFingerprint())) {
            throw new RuntimeWorkloadIdentityException("A different workload credential rotation is already in progress");
        }
    }

    private static void requireOwner(StoredCredential stored, String ownerFingerprint) {
        if (!stored.ownerFingerprint().equals(ownerFingerprint)) {
            throw new RuntimeWorkloadIdentityException("The workload credential belongs to another immutable cell binding");
        }
    }

    private static void requireOwner(
            StoredRegistrationHandoff stored, String ownerFingerprint) {
        if (!stored.ownerFingerprint().equals(ownerFingerprint)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff belongs to another immutable cell binding");
        }
    }

    private static void requireOwner(
            StoredRegistrationDeletionIntent stored, String ownerFingerprint) {
        if (!stored.ownerFingerprint().equals(ownerFingerprint)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration deletion belongs to another immutable cell binding");
        }
    }

    private static void requireCapability(
            StoredRegistrationHandoff stored, String expectedCapabilityFingerprint) {
        byte[] capability = Base64.getUrlDecoder().decode(stored.handoffCapability());
        try {
            if (!constantTimeEquals(
                    fingerprint(capability), expectedCapabilityFingerprint)) {
                throw new RuntimeWorkloadIdentityException(
                        "The workload registration handoff changed concurrently");
            }
        } finally {
            Arrays.fill(capability, (byte) 0);
        }
    }

    private static boolean constantTimeEquals(String left, String right) {
        return left != null
                && right != null
                && MessageDigest.isEqual(
                        left.getBytes(StandardCharsets.US_ASCII),
                        right.getBytes(StandardCharsets.US_ASCII));
    }

    private static void requireMethod(
            StoredCredential stored,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod) {
        if (!stored.authenticationMethod().equals(authenticationMethod.name())) {
            throw new RuntimeWorkloadIdentityException("The workload credential authentication method cannot be rebound");
        }
    }

    private <T> T locked(String clientId, Callable<T> operation) {
        Path target = pathForRef(credentialRef(clientId));
        ReentrantLock local = stripe(CREDENTIAL_LOCKS, target);
        local.lock();
        try {
            ensureDirectory(target.getParent());
            Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
            try (FileChannel channel = openOwnerLockFile(lockPath);
                    FileLock ignored = channel.lock()) {
                return operation.call();
            } catch (RuntimeWorkloadIdentityException exception) {
                throw exception;
            } catch (Exception exception) {
                throw unavailable("The workload credential lock is unavailable", exception);
            }
        } finally {
            local.unlock();
        }
    }

    private static ReentrantLock[] lockStripes() {
        ReentrantLock[] locks = new ReentrantLock[LOCAL_LOCK_STRIPES];
        Arrays.setAll(locks, ignored -> new ReentrantLock());
        return locks;
    }

    private static ReentrantLock stripe(ReentrantLock[] locks, Path path) {
        return locks[Math.floorMod(path.hashCode(), locks.length)];
    }

    private static String credentialRef(String clientId) {
        requireClientId(clientId);
        return "credentialref://weave/agent-runtime/cells/" + clientId;
    }

    private Path handoffDirectory() {
        Path directory = root.resolve("weave/agent-runtime/registration-handoffs")
                .toAbsolutePath()
                .normalize();
        if (!directory.startsWith(root)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff path escaped its SecretRef root");
        }
        requireNoSymlinkAncestors(directory);
        return directory;
    }

    private Path handoffPath(String clientId) {
        requireClientId(clientId);
        Path path = handoffDirectory().resolve(clientId).toAbsolutePath().normalize();
        if (!path.startsWith(handoffDirectory())) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration handoff path escaped its SecretRef root");
        }
        requireNoSymlinkAncestors(path.getParent());
        return path;
    }

    private Path lifecycleLockPath(String clientId) {
        requireClientId(clientId);
        Path directory = root.resolve("weave/agent-runtime/registration-lifecycle-locks")
                .toAbsolutePath()
                .normalize();
        if (!directory.startsWith(root)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration lifecycle lock path escaped its SecretRef root");
        }
        requireNoSymlinkAncestors(directory);
        Path path = directory.resolve(clientId + ".lock").toAbsolutePath().normalize();
        if (!path.startsWith(directory)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration lifecycle lock path escaped its SecretRef root");
        }
        return path;
    }

    private Path deletionIntentPath(String clientId) {
        requireClientId(clientId);
        Path directory = root.resolve("weave/agent-runtime/registration-deletions")
                .toAbsolutePath()
                .normalize();
        if (!directory.startsWith(root)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration deletion path escaped its SecretRef root");
        }
        requireNoSymlinkAncestors(directory);
        Path path = directory.resolve(clientId).toAbsolutePath().normalize();
        if (!path.startsWith(directory)) {
            throw new RuntimeWorkloadIdentityException(
                    "The workload registration deletion path escaped its SecretRef root");
        }
        return path;
    }

    private Path pathForRef(String credentialRef) {
        try {
            URI uri = URI.create(credentialRef);
            if (!"credentialref".equals(uri.getScheme())
                    || !"weave".equals(uri.getHost())
                    || uri.getPort() != -1
                    || uri.getUserInfo() != null
                    || uri.getQuery() != null
                    || uri.getFragment() != null
                    || uri.getRawPath() == null
                    || uri.getRawPath().contains("%")) {
                throw new IllegalArgumentException("invalid SecretRef");
            }
            List<String> segments = Arrays.stream(uri.getRawPath().split("/"))
                    .filter(value -> !value.isEmpty())
                    .toList();
            if (segments.isEmpty() || segments.stream().anyMatch(value ->
                    ".".equals(value) || "..".equals(value) || !SEGMENT.matcher(value).matches())) {
                throw new IllegalArgumentException("invalid SecretRef path");
            }
            Path path = root.resolve(uri.getHost());
            for (String segment : segments) {
                path = path.resolve(segment);
            }
            path = path.toAbsolutePath().normalize();
            if (!path.startsWith(root)) {
                throw new IllegalArgumentException("SecretRef escaped its configured root");
            }
            requireNoSymlinkAncestors(path.getParent());
            return path;
        } catch (IllegalArgumentException exception) {
            throw new RuntimeWorkloadIdentityException("The SecretRef is invalid", exception);
        }
    }

    private void requireNoSymlinkAncestors(Path candidate) {
        Path current = root;
        if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
            throw new RuntimeWorkloadIdentityException("The SecretRef root must not be a symbolic link");
        }
        Path relative = root.relativize(candidate.toAbsolutePath().normalize());
        for (Path segment : relative) {
            current = current.resolve(segment);
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new RuntimeWorkloadIdentityException("A SecretRef path must not traverse symbolic links");
            }
        }
    }

    private static void requireSecureRegularFile(Path path) throws IOException {
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new RuntimeWorkloadIdentityException("The SecretRef target must be a regular non-symlink file");
        }
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> forbidden = EnumSet.of(
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_WRITE,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_WRITE,
                    PosixFilePermission.OTHERS_EXECUTE);
            if (permissions.stream().anyMatch(forbidden::contains)) {
                throw new RuntimeWorkloadIdentityException("The SecretRef target permissions are too broad");
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystems rely on their platform ACLs; symlink and regular-file checks still apply.
        }
    }

    private static void ensureDirectory(Path directory) {
        try {
            if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(directory)) {
                throw new RuntimeWorkloadIdentityException("SecretRef directories must not be symbolic links");
            }
            try {
                Files.createDirectories(directory,
                        PosixFilePermissions.asFileAttribute(OWNER_DIRECTORY_PERMISSIONS));
            } catch (UnsupportedOperationException unsupported) {
                Files.createDirectories(directory);
            }
            setFilePermissions(directory, OWNER_DIRECTORY_PERMISSIONS);
        } catch (IOException exception) {
            throw unavailable("Unable to prepare the SecretRef directory", exception);
        }
    }

    private static Path createOwnerTempFile(Path directory, String prefix, String suffix) throws IOException {
        try {
            return Files.createTempFile(
                    directory, prefix, suffix,
                    PosixFilePermissions.asFileAttribute(OWNER_FILE_PERMISSIONS));
        } catch (UnsupportedOperationException unsupported) {
            return Files.createTempFile(directory, prefix, suffix);
        }
    }

    private static FileChannel openOwnerLockFile(Path lockPath) throws IOException {
        if (Files.exists(lockPath, LinkOption.NOFOLLOW_LINKS)
                && (!Files.isRegularFile(lockPath, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(lockPath))) {
            throw new RuntimeWorkloadIdentityException(
                    "The SecretRef lock must be a regular non-symlink file");
        }
        Set<java.nio.file.OpenOption> options = Set.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.WRITE,
                LinkOption.NOFOLLOW_LINKS);
        FileChannel channel;
        try {
            channel = FileChannel.open(
                    lockPath,
                    options,
                    PosixFilePermissions.asFileAttribute(OWNER_FILE_PERMISSIONS));
        } catch (UnsupportedOperationException unsupported) {
            channel = FileChannel.open(lockPath, options);
        }
        try {
            setFilePermissions(lockPath, OWNER_FILE_PERMISSIONS);
            requireSecureRegularFile(lockPath);
            return channel;
        } catch (IOException | RuntimeException failure) {
            channel.close();
            throw failure;
        }
    }

    private static void setFilePermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // The platform ACL is authoritative on non-POSIX filesystems.
        }
    }

    private static void forceDirectory(Path directory) {
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // The credential file itself was forced before its atomic rename.
        }
    }

    private static void requireClientId(String value) {
        if (value == null || !value.matches("weaver-cell-[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("clientId must use the weaver-cell-{id} namespace");
        }
    }

    private static RuntimeWorkloadIdentityException unavailable(String message, Throwable cause) {
        return new RuntimeWorkloadIdentityException(message, cause);
    }

    private record StoredCredential(
            String schemaVersion,
            String clientId,
            String ownerFingerprint,
            String authenticationMethod,
            String credentialRef,
            String registrationUri,
            String registrationAccessToken,
            String serviceAccountSubject,
            boolean registrationEnabled,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            String activeKeyId,
            String rotationFingerprint,
            String lastCompletedRotationFingerprint,
            List<StoredKey> keys) {

        StoredCredential withRotation(String nextRotationFingerprint, List<StoredKey> nextKeys) {
            return new StoredCredential(
                    schemaVersion, clientId, ownerFingerprint, authenticationMethod, credentialRef,
                    registrationUri, registrationAccessToken, serviceAccountSubject,
                    registrationEnabled, organizationFingerprint, personFingerprint,
                    cellFingerprint,
                    activeKeyId, nextRotationFingerprint, lastCompletedRotationFingerprint,
                    List.copyOf(nextKeys));
        }
    }

    record RegistrationAuthority(
            URI registrationUri,
            String serviceAccountSubject,
            boolean enabled,
            String tokenFingerprint,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint) {}

    record RegistrationAuthorityEntry(
            String clientId,
            String ownerFingerprint,
            String credentialRef,
            Set<String> acceptedKeyIds,
            RegistrationAuthority authority) {}

    enum RegistrationHandoffPhase {
        PREPARED,
        STAGED
    }

    enum RegistrationHandoffOperation {
        CREATE("create"),
        ROTATE("rotate"),
        DISABLE("disable"),
        REENABLE("reenable");

        private final String wireValue;

        RegistrationHandoffOperation(String wireValue) {
            this.wireValue = wireValue;
        }

        String wireValue() {
            return wireValue;
        }
    }

    record RegistrationHandoff(
            URI registrationUri,
            String intendedStateDigest,
            String intendedPublicJwks,
            String expectedSubjectDigest,
            String currentAuthorityFingerprint,
            String capabilityFingerprint,
            String replacementTokenFingerprint,
            String observedSubjectDigest,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            boolean targetEnabled,
            RegistrationHandoffPhase phase,
            RegistrationHandoffOperation operation,
            int attemptCount,
            Instant createdAt) {}

    record RegistrationHandoffEntry(
            String clientId,
            String ownerFingerprint,
            RegistrationHandoff handoff) {}

    record RegistrationDeletionIntent(
            URI registrationUri,
            String authorityFingerprint,
            Instant createdAt) {}

    private record RegistrationTokenSnapshot(
            RegistrationAuthority authority, byte[] registrationAccessToken) {}

    private record RegistrationHandoffSecretSnapshot(
            RegistrationHandoff handoff,
            byte[] capability,
            byte[] replacementRegistrationAccessToken) {}

    @FunctionalInterface
    interface RegistrationAccessTokenOperation<T> {
        T apply(RegistrationAuthority authority, byte[] registrationAccessToken);
    }

    @FunctionalInterface
    interface RegistrationHandoffSecretOperation<T> {
        T apply(
                RegistrationHandoff handoff,
                byte[] capability,
                byte[] replacementRegistrationAccessToken);
    }

    @FunctionalInterface
    interface PrivateJwkOperation<T> {
        T apply(byte[] privateJwk) throws Exception;
    }

    private record StoredKey(String keyId, KeyStatus status, String createdAt, Map<String, String> privateJwk) {
        StoredKey {
            privateJwk = privateJwk == null ? null : Map.copyOf(privateJwk);
        }

        StoredKey withStatus(KeyStatus nextStatus) {
            return new StoredKey(keyId, nextStatus, createdAt, privateJwk);
        }
    }

    private enum KeyStatus {
        ACTIVE,
        PENDING,
        PREVIOUS
    }

    private record StoredRegistrationHandoff(
            String schemaVersion,
            String clientId,
            String ownerFingerprint,
            String registrationUri,
            String organizationFingerprint,
            String personFingerprint,
            String cellFingerprint,
            String intendedStateDigest,
            String intendedPublicJwks,
            String expectedSubjectDigest,
            String currentAuthorityFingerprint,
            String handoffCapability,
            String replacementRegistrationAccessToken,
            String observedSubjectDigest,
            boolean targetEnabled,
            RegistrationHandoffPhase phase,
            RegistrationHandoffOperation operation,
            int attemptCount,
            String createdAt) {}

    private record StoredRegistrationDeletionIntent(
            String schemaVersion,
            String clientId,
            String ownerFingerprint,
            String registrationUri,
            String authorityFingerprint,
            String createdAt) {}
}
