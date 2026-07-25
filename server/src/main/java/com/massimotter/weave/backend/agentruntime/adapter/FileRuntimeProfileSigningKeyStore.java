package com.massimotter.weave.backend.agentruntime.adapter;

import tools.jackson.core.JacksonException;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyLifecycle;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileSigningKeyProvider;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * File-backed RuntimeProfile signing trust root for self-hosted deployments.
 *
 * <p>Only PKCS#8 private material is stored in this operator-mounted secret
 * root. The manifest contains public X.509 keys and support-safe hashes. Every
 * mutation is process-locked, fsync'd, and atomically published. Symlinks and
 * group/world-accessible private files are rejected.</p>
 */
public final class FileRuntimeProfileSigningKeyStore implements
        RuntimeProfileSigningKeyProvider,
        RuntimeProfileTrustKeyProvider,
        RuntimeProfileSigningKeyLifecycle {

    private static final String SCHEMA = "weave.runtime-profile-signing-keys/v1";
    private static final String MANIFEST_NAME = "runtime-profile-signing-keys.json";
    private static final String LOCK_NAME = ".runtime-profile-signing-keys.lock";
    private static final int MAX_MANIFEST_BYTES = 262_144;
    private static final int MAX_PRIVATE_KEY_BYTES = 4_096;
    private static final Pattern KEY_ID = Pattern.compile("rpk_[A-Za-z0-9_-]{20,64}");
    private static final Pattern HASH = Pattern.compile("sha256:[a-f0-9]{64}");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
            PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> SECRET_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE);
    private static final byte[] KEY_PAIR_PROOF =
            "weave-runtime-profile-key-pair-proof/v1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

    private final Path root;
    private final Path manifestPath;
    private final Path lockPath;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final SecureRandom secureRandom;
    private final Duration keyLifetime;
    private final Duration trustOverlap;
    private final Duration maximumProfileTtl;
    private final ReentrantLock localLock = new ReentrantLock();

    public FileRuntimeProfileSigningKeyStore(
            Path root,
            ObjectMapper objectMapper,
            Clock clock,
            SecureRandom secureRandom,
            Duration keyLifetime,
            Duration trustOverlap,
            Duration maximumProfileTtl) {
        if (root == null || objectMapper == null || clock == null || secureRandom == null) {
            throw new IllegalArgumentException("signing-key store root, mapper, clock, and randomness are required");
        }
        validateDurations(keyLifetime, trustOverlap, maximumProfileTtl);
        this.root = root.toAbsolutePath().normalize();
        this.manifestPath = this.root.resolve(MANIFEST_NAME);
        this.lockPath = this.root.resolve(LOCK_NAME);
        this.mapper = objectMapper.rebuild()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .build();
        this.clock = clock;
        this.secureRandom = secureRandom;
        this.keyLifetime = keyLifetime;
        this.trustOverlap = trustOverlap;
        this.maximumProfileTtl = maximumProfileTtl;
        ensureSecretDirectory();
    }

    @Override
    public SigningKey activeKey() {
        return readLocked(() -> {
            StoredManifest manifest = requireManifest();
            Instant now = clock.instant();
            StoredKey active = active(manifest);
            if (now.isBefore(active.validFrom()) || !active.validUntil().isAfter(now.plus(maximumProfileTtl))) {
                throw unavailable("The RuntimeProfile signing key is outside its safe issuance window", null);
            }
            KeyPair material = readKeyPair(active);
            return new SigningKey(active.keyId(), material.getPrivate(), material.getPublic());
        });
    }

    @Override
    public Optional<TrustKey> resolve(String keyId, Instant now) {
        if (keyId == null || !KEY_ID.matcher(keyId).matches() || now == null) {
            return Optional.empty();
        }
        return readLocked(() -> readManifest()
                .flatMap(manifest -> manifest.keys().stream()
                        .filter(key -> key.keyId().equals(keyId))
                        .map(this::trustKey)
                        .filter(key -> key.validAt(now))
                        .findFirst()));
    }

    @Override
    public List<TrustKey> publishedKeys(Instant now) {
        Objects.requireNonNull(now, "trust publication time");
        return readLocked(() -> readManifest()
                .map(manifest -> manifest.keys().stream()
                        .map(this::trustKey)
                        .filter(key -> key.validAt(now))
                        .sorted(Comparator.comparing(TrustKey::keyId))
                        .toList())
                .orElseGet(List::of));
    }

    @Override
    public KeyRingState initialize(String operationRef) {
        String operationHash = referenceHash(operationRef, "initialization operation reference");
        return writeLocked(() -> {
            Optional<StoredManifest> existing = readManifest();
            if (existing.isPresent()) {
                if (!operationHash.equals(existing.orElseThrow().initializationRefHash())) {
                    throw conflict("The RuntimeProfile signing trust root is already initialized");
                }
                cleanupOrphanPrivateKeys(existing.orElseThrow());
                return projection(existing.orElseThrow());
            }
            Instant now = clock.instant();
            GeneratedKey generated = generate(RuntimeProfileSigningKeyLifecycle.Status.ACTIVE, now);
            try {
                writePrivateKey(generated.stored(), generated.privateBytes());
            } finally {
                Arrays.fill(generated.privateBytes(), (byte) 0);
            }
            StoredManifest created = new StoredManifest(
                    SCHEMA,
                    operationHash,
                    generated.stored().keyId(),
                    null,
                    null,
                    null,
                    List.of(generated.stored()));
            validate(created, true);
            writeManifest(created);
            cleanupOrphanPrivateKeys(created);
            return projection(created);
        });
    }

    @Override
    public KeyRingState prepareRotation(String rotationRef) {
        String rotationHash = referenceHash(rotationRef, "rotation reference");
        return writeLocked(() -> {
            StoredManifest current = requireManifest();
            if (rotationHash.equals(current.lastCompletedRotationRefHash())
                    && current.activeRotationRefHash() == null) {
                cleanupOrphanPrivateKeys(current);
                return projection(current);
            }
            if (current.activeRotationRefHash() != null) {
                requireSameRotation(current, rotationHash);
                cleanupOrphanPrivateKeys(current);
                return projection(current);
            }
            Instant now = clock.instant();
            GeneratedKey generated = generate(RuntimeProfileSigningKeyLifecycle.Status.PENDING, now);
            if (current.keys().stream().anyMatch(key -> key.keyId().equals(generated.stored().keyId()))) {
                throw unavailable("A RuntimeProfile signing key identifier collision occurred", null);
            }
            try {
                writePrivateKey(generated.stored(), generated.privateBytes());
            } finally {
                Arrays.fill(generated.privateBytes(), (byte) 0);
            }
            List<StoredKey> keys = new ArrayList<>(current.keys());
            keys.add(generated.stored());
            StoredManifest prepared = new StoredManifest(
                    current.schemaVersion(),
                    current.initializationRefHash(),
                    current.activeKeyId(),
                    generated.stored().keyId(),
                    rotationHash,
                    current.lastCompletedRotationRefHash(),
                    List.copyOf(keys));
            validate(prepared, true);
            writeManifest(prepared);
            cleanupOrphanPrivateKeys(prepared);
            return projection(prepared);
        });
    }

    @Override
    public KeyRingState activateRotation(String rotationRef) {
        String rotationHash = referenceHash(rotationRef, "rotation reference");
        return writeLocked(() -> {
            StoredManifest current = requireManifest();
            if (rotationHash.equals(current.lastCompletedRotationRefHash())
                    && current.activeRotationRefHash() == null) {
                cleanupOrphanPrivateKeys(current);
                return projection(current);
            }
            requireSameRotation(current, rotationHash);
            if (current.pendingKeyId() == null) {
                cleanupOrphanPrivateKeys(current);
                return projection(current);
            }
            Instant now = clock.instant();
            Instant previousValidUntil = now.plus(trustOverlap);
            List<StoredKey> activatedKeys = current.keys().stream()
                    .map(key -> {
                        if (key.keyId().equals(current.pendingKeyId())) {
                            if (!key.validUntil().isAfter(now.plus(maximumProfileTtl))) {
                                throw conflict("The prepared RuntimeProfile signing key has expired");
                            }
                            return key.withStatus(RuntimeProfileSigningKeyLifecycle.Status.ACTIVE);
                        }
                        if (key.keyId().equals(current.activeKeyId())) {
                            return new StoredKey(
                                    key.keyId(),
                                    RuntimeProfileSigningKeyLifecycle.Status.PREVIOUS,
                                    key.publicKeyX509(),
                                    null,
                                    key.validFrom(),
                                    previousValidUntil);
                        }
                        return key;
                    })
                    .toList();
            StoredManifest activated = new StoredManifest(
                    current.schemaVersion(),
                    current.initializationRefHash(),
                    current.pendingKeyId(),
                    null,
                    current.activeRotationRefHash(),
                    current.lastCompletedRotationRefHash(),
                    activatedKeys);
            validate(activated, true);
            writeManifest(activated);
            cleanupOrphanPrivateKeys(activated);
            return projection(activated);
        });
    }

    @Override
    public KeyRingState completeRetirement(String rotationRef) {
        String rotationHash = referenceHash(rotationRef, "rotation reference");
        return writeLocked(() -> {
            StoredManifest current = requireManifest();
            if (rotationHash.equals(current.lastCompletedRotationRefHash())
                    && current.activeRotationRefHash() == null) {
                cleanupOrphanPrivateKeys(current);
                return projection(current);
            }
            requireSameRotation(current, rotationHash);
            if (current.pendingKeyId() != null) {
                throw conflict("The RuntimeProfile signing-key rotation has not been activated");
            }
            Instant now = clock.instant();
            if (current.keys().stream()
                    .filter(key -> key.status() == RuntimeProfileSigningKeyLifecycle.Status.PREVIOUS)
                    .anyMatch(key -> key.validUntil().isAfter(now))) {
                throw conflict("The RuntimeProfile signing-key trust overlap has not elapsed");
            }
            List<StoredKey> activeOnly = current.keys().stream()
                    .filter(key -> key.status() == RuntimeProfileSigningKeyLifecycle.Status.ACTIVE)
                    .toList();
            StoredManifest retired = new StoredManifest(
                    current.schemaVersion(),
                    current.initializationRefHash(),
                    current.activeKeyId(),
                    null,
                    null,
                    rotationHash,
                    activeOnly);
            validate(retired, true);
            writeManifest(retired);
            cleanupOrphanPrivateKeys(retired);
            return projection(retired);
        });
    }

    @Override
    public KeyRingState current() {
        return readLocked(() -> projection(requireManifest()));
    }

    private GeneratedKey generate(RuntimeProfileSigningKeyLifecycle.Status status, Instant now) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
            generator.initialize(NamedParameterSpec.ED25519, secureRandom);
            KeyPair pair = generator.generateKeyPair();
            String keyId = keyId(pair.getPublic());
            String fileName = privateFileName(keyId);
            StoredKey stored = new StoredKey(
                    keyId,
                    status,
                    Base64.getUrlEncoder().withoutPadding().encodeToString(pair.getPublic().getEncoded()),
                    fileName,
                    now,
                    now.plus(keyLifetime));
            return new GeneratedKey(stored, pair.getPrivate().getEncoded());
        } catch (GeneralSecurityException exception) {
            throw unavailable("JDK Ed25519 key generation is unavailable", exception);
        }
    }

    private KeyPair readKeyPair(StoredKey key) {
        Path privatePath = privatePath(key);
        byte[] privateBytes = readBounded(privatePath, MAX_PRIVATE_KEY_BYTES, "RuntimeProfile private key");
        try {
            KeyFactory factory = KeyFactory.getInstance("Ed25519");
            PrivateKey privateKey = factory.generatePrivate(new PKCS8EncodedKeySpec(privateBytes));
            PublicKey publicKey = publicKey(key);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(privateKey);
            signature.update(KEY_PAIR_PROOF);
            byte[] proof = signature.sign();
            try {
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(publicKey);
                verifier.update(KEY_PAIR_PROOF);
                if (!verifier.verify(proof)) {
                    throw unavailable("The active RuntimeProfile signing key pair does not match", null);
                }
            } finally {
                Arrays.fill(proof, (byte) 0);
            }
            return new KeyPair(publicKey, privateKey);
        } catch (GeneralSecurityException exception) {
            throw unavailable("The RuntimeProfile private key is invalid", exception);
        } finally {
            Arrays.fill(privateBytes, (byte) 0);
        }
    }

    private TrustKey trustKey(StoredKey key) {
        return new TrustKey(key.keyId(), publicKey(key), key.validFrom(), key.validUntil());
    }

    private PublicKey publicKey(StoredKey key) {
        try {
            byte[] encoded = Base64.getUrlDecoder().decode(key.publicKeyX509());
            return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
        } catch (IllegalArgumentException | GeneralSecurityException exception) {
            throw unavailable("The RuntimeProfile public signing key is invalid", exception);
        }
    }

    private void writePrivateKey(StoredKey key, byte[] privateBytes) {
        if (privateBytes == null || privateBytes.length == 0 || privateBytes.length > MAX_PRIVATE_KEY_BYTES) {
            throw unavailable("The generated RuntimeProfile private key encoding is invalid", null);
        }
        Path target = privatePath(key);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw conflict("The generated RuntimeProfile private-key target already exists");
        }
        atomicWrite(target, privateBytes);
    }

    private Optional<StoredManifest> readManifest() {
        if (!Files.exists(manifestPath, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        rejectSymlink(manifestPath, "RuntimeProfile signing-key manifest");
        byte[] bytes = readBounded(manifestPath, MAX_MANIFEST_BYTES, "RuntimeProfile signing-key manifest");
        try {
            StoredManifest manifest = mapper.readValue(bytes, StoredManifest.class);
            validate(manifest, true);
            return Optional.of(manifest);
        } catch (RuntimeProfileSigningKeyException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable("The RuntimeProfile signing-key manifest is invalid", exception);
        } finally {
            Arrays.fill(bytes, (byte) 0);
        }
    }

    private StoredManifest requireManifest() {
        return readManifest().orElseThrow(() -> unavailable(
                "The RuntimeProfile signing trust root is not initialized", null));
    }

    private void writeManifest(StoredManifest manifest) {
        try {
            byte[] bytes = mapper.writeValueAsBytes(manifest);
            if (bytes.length > MAX_MANIFEST_BYTES) {
                throw unavailable("The RuntimeProfile signing-key manifest exceeds its size bound", null);
            }
            atomicWrite(manifestPath, bytes);
            Arrays.fill(bytes, (byte) 0);
        } catch (RuntimeProfileSigningKeyException exception) {
            throw exception;
        } catch (JacksonException exception) {
            throw unavailable("Unable to serialize the RuntimeProfile signing-key manifest", exception);
        }
    }

    private void atomicWrite(Path target, byte[] bytes) {
        Path temporary = root.resolve("." + target.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            rejectExistingPath(temporary, "temporary signing-key file");
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE)) {
                channel.write(java.nio.ByteBuffer.wrap(bytes));
                channel.force(true);
            }
            setSecretPermissions(temporary);
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            setSecretPermissions(target);
            forceDirectory();
        } catch (IOException exception) {
            try {
                Files.deleteIfExists(temporary);
            } catch (IOException ignored) {
                // Preserve the original support-safe failure.
            }
            throw unavailable("Unable to atomically persist RuntimeProfile signing-key state", exception);
        }
    }

    private byte[] readBounded(Path path, int maximumBytes, String description) {
        rejectSymlink(path, description);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
                throw unavailable(description + " is not a regular file", null);
            }
            long size = Files.size(path);
            if (size < 1 || size > maximumBytes) {
                throw unavailable(description + " violates its size bound", null);
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length < 1 || bytes.length > maximumBytes) {
                Arrays.fill(bytes, (byte) 0);
                throw unavailable(description + " violates its size bound", null);
            }
            requireSecretPermissions(path, description);
            return bytes;
        } catch (RuntimeProfileSigningKeyException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("Unable to read " + description, exception);
        }
    }

    private void validate(StoredManifest manifest, boolean requirePrivateFiles) {
        if (manifest == null
                || !SCHEMA.equals(manifest.schemaVersion())
                || !matchesHash(manifest.initializationRefHash())
                || !matchesKeyId(manifest.activeKeyId())
                || manifest.keys() == null
                || manifest.keys().isEmpty()
                || manifest.keys().size() > 3) {
            throw unavailable("The RuntimeProfile signing-key manifest violates its contract", null);
        }
        requireOptionalHash(manifest.activeRotationRefHash(), "active rotation reference hash");
        requireOptionalHash(manifest.lastCompletedRotationRefHash(), "completed rotation reference hash");
        if (manifest.pendingKeyId() != null && !matchesKeyId(manifest.pendingKeyId())) {
            throw unavailable("The RuntimeProfile pending signing-key identifier is invalid", null);
        }
        Set<String> keyIds = new HashSet<>();
        long activeCount = 0;
        long pendingCount = 0;
        long previousCount = 0;
        for (StoredKey key : manifest.keys()) {
            validateKey(key, requirePrivateFiles);
            if (!keyIds.add(key.keyId())) {
                throw unavailable("The RuntimeProfile signing-key manifest contains duplicate key identifiers", null);
            }
            if (key.status() == RuntimeProfileSigningKeyLifecycle.Status.ACTIVE) {
                activeCount++;
            } else if (key.status() == RuntimeProfileSigningKeyLifecycle.Status.PENDING) {
                pendingCount++;
            } else if (key.status() == RuntimeProfileSigningKeyLifecycle.Status.PREVIOUS) {
                previousCount++;
            }
        }
        StoredKey active = manifest.keys().stream()
                .filter(key -> key.status() == RuntimeProfileSigningKeyLifecycle.Status.ACTIVE)
                .findFirst()
                .orElse(null);
        StoredKey pending = manifest.keys().stream()
                .filter(key -> key.status() == RuntimeProfileSigningKeyLifecycle.Status.PENDING)
                .findFirst()
                .orElse(null);
        boolean preparing = manifest.pendingKeyId() != null;
        boolean rotating = manifest.activeRotationRefHash() != null;
        if (activeCount != 1
                || previousCount > 1
                || pendingCount > 1
                || active == null
                || !active.keyId().equals(manifest.activeKeyId())
                || preparing != (pendingCount == 1)
                || (preparing && !manifest.pendingKeyId().equals(pending.keyId()))
                || preparing != (rotating && previousCount == 0)
                || (!preparing && rotating != (previousCount == 1))
                || (!rotating && previousCount != 0)) {
            throw unavailable("The RuntimeProfile signing-key lifecycle state is inconsistent", null);
        }
    }

    private void validateKey(StoredKey key, boolean requirePrivateFile) {
        if (key == null
                || !matchesKeyId(key.keyId())
                || key.status() == null
                || key.publicKeyX509() == null
                || key.publicKeyX509().isBlank()
                || key.validFrom() == null
                || key.validUntil() == null
                || !key.validUntil().isAfter(key.validFrom())) {
            throw unavailable("The RuntimeProfile signing-key metadata is invalid", null);
        }
        PublicKey publicKey = publicKey(key);
        if (!key.keyId().equals(keyId(publicKey))) {
            throw unavailable("The RuntimeProfile signing-key identifier does not match its public key", null);
        }
        boolean privateRequired = key.status() != RuntimeProfileSigningKeyLifecycle.Status.PREVIOUS;
        if (privateRequired) {
            if (!privateFileName(key.keyId()).equals(key.privateKeyFile())) {
                throw unavailable("The RuntimeProfile private-key reference is invalid", null);
            }
            if (requirePrivateFile) {
                Path path = privatePath(key);
                if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw unavailable("Required RuntimeProfile private-key material is unavailable", null);
                }
                rejectSymlink(path, "RuntimeProfile private key");
                requireSecretPermissions(path, "RuntimeProfile private key");
            }
        } else if (key.privateKeyFile() != null) {
            throw unavailable("A retired RuntimeProfile signing key must not retain private material", null);
        }
    }

    private StoredKey active(StoredManifest manifest) {
        return manifest.keys().stream()
                .filter(key -> key.status() == RuntimeProfileSigningKeyLifecycle.Status.ACTIVE)
                .findFirst()
                .orElseThrow(() -> unavailable("The RuntimeProfile active signing key is unavailable", null));
    }

    private KeyRingState projection(StoredManifest manifest) {
        List<PublishedKeyState> keys = manifest.keys().stream()
                .sorted(Comparator.comparing(StoredKey::keyId))
                .map(key -> new PublishedKeyState(
                        key.keyId(),
                        key.status(),
                        key.validFrom(),
                        key.validUntil(),
                        key.privateKeyFile() != null
                                && Files.exists(privatePath(key), LinkOption.NOFOLLOW_LINKS)))
                .toList();
        return new KeyRingState(
                manifest.activeKeyId(),
                manifest.pendingKeyId(),
                keys,
                manifest.activeRotationRefHash(),
                manifest.lastCompletedRotationRefHash());
    }

    private void cleanupOrphanPrivateKeys(StoredManifest manifest) {
        Set<String> referenced = manifest.keys().stream()
                .map(StoredKey::privateKeyFile)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (Stream<Path> entries = Files.list(root)) {
            entries.filter(path -> path.getFileName().toString().startsWith("key-rpk_"))
                    .filter(path -> path.getFileName().toString().endsWith(".pk8"))
                    .filter(path -> !referenced.contains(path.getFileName().toString()))
                    .forEach(path -> {
                        rejectSymlink(path, "orphan RuntimeProfile private key");
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw unavailable("Unable to delete orphan RuntimeProfile private-key material", exception);
                        }
                    });
            forceDirectory();
        } catch (RuntimeProfileSigningKeyException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("Unable to inspect RuntimeProfile private-key material", exception);
        }
    }

    private Path privatePath(StoredKey key) {
        if (key.privateKeyFile() == null || !privateFileName(key.keyId()).equals(key.privateKeyFile())) {
            throw unavailable("The RuntimeProfile private-key reference is invalid", null);
        }
        Path candidate = root.resolve(key.privateKeyFile()).normalize();
        if (!candidate.getParent().equals(root)) {
            throw unavailable("The RuntimeProfile private-key reference escapes its SecretRef root", null);
        }
        return candidate;
    }

    private void ensureSecretDirectory() {
        try {
            Files.createDirectories(root);
            rejectSymlink(root, "RuntimeProfile signing-key SecretRef root");
            if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                throw unavailable("The RuntimeProfile signing-key SecretRef root is not a directory", null);
            }
            setDirectoryPermissions(root);
            requireDirectoryPermissions(root);
        } catch (RuntimeProfileSigningKeyException exception) {
            throw exception;
        } catch (IOException exception) {
            throw unavailable("Unable to initialize the RuntimeProfile signing-key SecretRef root", exception);
        }
    }

    private <T> T readLocked(CheckedSupplier<T> operation) {
        localLock.lock();
        try {
            return operation.get();
        } finally {
            localLock.unlock();
        }
    }

    private <T> T writeLocked(CheckedSupplier<T> operation) {
        localLock.lock();
        try {
            rejectSymlink(lockPath, "RuntimeProfile signing-key lock");
            try (FileChannel channel = FileChannel.open(
                    lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
                    FileLock ignored = channel.lock()) {
                setSecretPermissions(lockPath);
                return operation.get();
            } catch (IOException exception) {
                throw unavailable("Unable to lock RuntimeProfile signing-key state", exception);
            }
        } finally {
            localLock.unlock();
        }
    }

    private void forceDirectory() {
        try (FileChannel channel = FileChannel.open(root, StandardOpenOption.READ)) {
            channel.force(true);
        } catch (UnsupportedOperationException ignored) {
            // Some mounted secret-store filesystems do not expose directory fsync.
        } catch (IOException exception) {
            throw unavailable("Unable to synchronize the RuntimeProfile signing-key SecretRef root", exception);
        }
    }

    private static void validateDurations(
            Duration keyLifetime,
            Duration trustOverlap,
            Duration maximumProfileTtl) {
        if (keyLifetime == null || keyLifetime.isNegative() || keyLifetime.isZero()
                || trustOverlap == null || trustOverlap.isNegative() || trustOverlap.isZero()
                || maximumProfileTtl == null || maximumProfileTtl.isNegative() || maximumProfileTtl.isZero()
                || trustOverlap.compareTo(maximumProfileTtl) < 0
                || keyLifetime.compareTo(trustOverlap.plus(maximumProfileTtl)) <= 0) {
            throw new IllegalArgumentException(
                    "signing-key lifetime must exceed trust overlap plus profile TTL, and overlap must cover profile TTL");
        }
    }

    private static void requireSameRotation(StoredManifest manifest, String rotationHash) {
        if (!rotationHash.equals(manifest.activeRotationRefHash())) {
            throw conflict("A different RuntimeProfile signing-key rotation is already active");
        }
    }

    private static String referenceHash(String reference, String field) {
        if (reference == null || reference.isBlank() || reference.length() > 512) {
            throw new IllegalArgumentException(field + " must contain between 1 and 512 characters");
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(reference.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return "sha256:" + java.util.HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
        }
    }

    private static String keyId(PublicKey publicKey) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(publicKey.getEncoded());
            return "rpk_" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException impossible) {
            throw new IllegalStateException("JDK SHA-256 support is unavailable", impossible);
        }
    }

    private static String privateFileName(String keyId) {
        if (!matchesKeyId(keyId)) {
            throw unavailable("The RuntimeProfile signing-key identifier is invalid", null);
        }
        return "key-" + keyId + ".pk8";
    }

    private static boolean matchesKeyId(String value) {
        return value != null && KEY_ID.matcher(value).matches();
    }

    private static boolean matchesHash(String value) {
        return value != null && HASH.matcher(value).matches();
    }

    private static void requireOptionalHash(String value, String field) {
        if (value != null && !matchesHash(value)) {
            throw unavailable("The RuntimeProfile " + field + " is invalid", null);
        }
    }

    private static void rejectSymlink(Path path, String description) {
        if (Files.isSymbolicLink(path)) {
            throw unavailable(description + " must not be a symbolic link", null);
        }
    }

    private static void rejectExistingPath(Path path, String description) {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            throw conflict("The " + description + " already exists");
        }
    }

    private static void setDirectoryPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, DIRECTORY_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some secret-store mounts do not expose POSIX permissions.
        }
    }

    private static void setSecretPermissions(Path path) throws IOException {
        try {
            Files.setPosixFilePermissions(path, SECRET_PERMISSIONS);
        } catch (UnsupportedOperationException ignored) {
            // Windows and some secret-store mounts do not expose POSIX permissions.
        }
    }

    private static void requireDirectoryPermissions(Path path) {
        try {
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!DIRECTORY_PERMISSIONS.containsAll(actual)) {
                throw unavailable("The RuntimeProfile signing-key SecretRef root is too broadly accessible", null);
            }
        } catch (UnsupportedOperationException ignored) {
            // No portable equivalent exists on this filesystem.
        } catch (IOException exception) {
            throw unavailable("Unable to validate the RuntimeProfile signing-key SecretRef root", exception);
        }
    }

    private static void requireSecretPermissions(Path path, String description) {
        try {
            Set<PosixFilePermission> actual = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (!SECRET_PERMISSIONS.containsAll(actual)) {
                throw unavailable(description + " is too broadly accessible", null);
            }
        } catch (UnsupportedOperationException ignored) {
            // No portable equivalent exists on this filesystem.
        } catch (IOException exception) {
            throw unavailable("Unable to validate " + description + " permissions", exception);
        }
    }

    private static RuntimeProfileSigningKeyException unavailable(String message, Throwable cause) {
        return cause == null
                ? new RuntimeProfileSigningKeyException(message)
                : new RuntimeProfileSigningKeyException(message, cause);
    }

    private static RuntimeProfileSigningKeyException conflict(String message) {
        return new RuntimeProfileSigningKeyException(message);
    }

    @FunctionalInterface
    private interface CheckedSupplier<T> {
        T get();
    }

    private record GeneratedKey(StoredKey stored, byte[] privateBytes) {
    }

    private record StoredManifest(
            String schemaVersion,
            String initializationRefHash,
            String activeKeyId,
            String pendingKeyId,
            String activeRotationRefHash,
            String lastCompletedRotationRefHash,
            List<StoredKey> keys) {
        private StoredManifest {
            keys = keys == null ? null : List.copyOf(keys);
        }
    }

    private record StoredKey(
            String keyId,
            RuntimeProfileSigningKeyLifecycle.Status status,
            String publicKeyX509,
            String privateKeyFile,
            Instant validFrom,
            Instant validUntil) {
        private StoredKey withStatus(RuntimeProfileSigningKeyLifecycle.Status nextStatus) {
            return new StoredKey(keyId, nextStatus, publicKeyX509, privateKeyFile, validFrom, validUntil);
        }
    }
}
