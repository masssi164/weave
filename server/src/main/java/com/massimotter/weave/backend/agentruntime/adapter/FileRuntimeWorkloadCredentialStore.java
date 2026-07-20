package com.massimotter.weave.backend.agentruntime.adapter;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

    static final String SCHEMA = "weave.workload-credential/v1";
    static final String ALGORITHM = "PS256";
    private static final int RSA_BITS = 3072;
    private static final int LOCAL_LOCK_STRIPES = 64;
    private static final int MAX_SECRET_BYTES = 64 * 1024;
    private static final Pattern SEGMENT = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Set<String> PRIVATE_JWK_FIELDS = Set.of(
            "kty", "use", "alg", "kid", "n", "e", "d", "p", "q", "dp", "dq", "qi");
    private static final Set<PosixFilePermission> OWNER_DIRECTORY_PERMISSIONS =
            PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> OWNER_FILE_PERMISSIONS =
            PosixFilePermissions.fromString("rw-------");

    private final Path root;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final ReentrantLock[] localLocks;

    public FileRuntimeWorkloadCredentialStore(Path root, ObjectMapper objectMapper) {
        this(root, objectMapper, Clock.systemUTC(), new SecureRandom());
    }

    FileRuntimeWorkloadCredentialStore(
            Path root,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom) {
        if (root == null || objectMapper == null || clock == null || secureRandom == null) {
            throw new IllegalArgumentException("credential root, ObjectMapper, clock, and randomness are required");
        }
        this.root = root.toAbsolutePath().normalize();
        this.mapper = objectMapper.copy()
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.localLocks = new ReentrantLock[LOCAL_LOCK_STRIPES];
        Arrays.setAll(localLocks, ignored -> new ReentrantLock());
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
                    current.authenticationMethod(), current.credentialRef(), pending.keyId(),
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
                    current.authenticationMethod(), current.credentialRef(), active.keyId(),
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
                    current.authenticationMethod(), current.credentialRef(), active.keyId(),
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

    private StoredCredential requireStored(String clientId, String ownerFingerprint) {
        StoredCredential current = read(clientId)
                .orElseThrow(() -> new RuntimeWorkloadIdentityException("The workload credential reference is unavailable"));
        requireOwner(current, ownerFingerprint);
        return current;
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
        } catch (IOException | RuntimeException exception) {
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
        } catch (IOException exception) {
            throw unavailable("Unable to encode the workload credential envelope", exception);
        }
        Path target = pathForRef(stored.credentialRef());
        ensureDirectory(target.getParent());
        Path temporary = null;
        try {
            temporary = createOwnerTempFile(target.getParent(), "." + stored.clientId() + "-", ".tmp");
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
            throw unavailable("Unable to atomically persist the workload credential", exception);
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
                    keyIds,
                    mapper.writeValueAsString(jwks),
                    phase(stored),
                    stored.rotationFingerprint());
        } catch (IOException exception) {
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

    private static void requireMethod(
            StoredCredential stored,
            RuntimeWorkloadBinding.AuthenticationMethod authenticationMethod) {
        if (!stored.authenticationMethod().equals(authenticationMethod.name())) {
            throw new RuntimeWorkloadIdentityException("The workload credential authentication method cannot be rebound");
        }
    }

    private <T> T locked(String clientId, Callable<T> operation) {
        ReentrantLock local = localLocks[Math.floorMod(clientId.hashCode(), localLocks.length)];
        local.lock();
        try {
            Path target = pathForRef(credentialRef(clientId));
            ensureDirectory(target.getParent());
            Path lockPath = target.resolveSibling(target.getFileName() + ".lock");
            try (FileChannel channel = FileChannel.open(
                            lockPath,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                setFilePermissions(lockPath, OWNER_FILE_PERMISSIONS);
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

    private static String credentialRef(String clientId) {
        requireClientId(clientId);
        return "credentialref://weave/agent-runtime/cells/" + clientId;
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
            String activeKeyId,
            String rotationFingerprint,
            String lastCompletedRotationFingerprint,
            List<StoredKey> keys) {

        StoredCredential withRotation(String nextRotationFingerprint, List<StoredKey> nextKeys) {
            return new StoredCredential(
                    schemaVersion, clientId, ownerFingerprint, authenticationMethod, credentialRef,
                    activeKeyId, nextRotationFingerprint, lastCompletedRotationFingerprint,
                    List.copyOf(nextKeys));
        }
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
}
