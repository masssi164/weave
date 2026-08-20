package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.ContentTargetUnavailableException;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.apache.opendal.AsyncOperator;
import org.apache.opendal.Entry;
import org.apache.opendal.ListOptions;
import org.apache.opendal.OpenDALException;
import org.apache.opendal.Operator;
import org.apache.opendal.layer.ConcurrentLimitLayer;
import org.apache.opendal.layer.RetryLayer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** OpenDAL-backed filesystem Infrastructure Adapter for weave-native immutable blobs. */
@Component
@Primary
public final class FilesystemBlobStore implements BlobStorePort {

    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = PosixFilePermissions.fromString("rw-------");
    private static final String STAGING_PREFIX = ".weave-native-staging/v1/";
    private static final Duration STAGING_RETENTION = Duration.ofHours(1);

    private final Path root;
    private final long maximumBlobBytes;
    private final int reconciliationLimit;
    private final AsyncOperator asyncOperator;
    private final Operator operator;
    private final DurabilitySync durabilitySync;
    private final StoredReadObserver readObserver;

    @Autowired
    public FilesystemBlobStore(WeaveNativeFilesProperties properties) {
        this(properties, DurabilitySync.system(), ignored -> { });
    }

    FilesystemBlobStore(
            WeaveNativeFilesProperties properties,
            DurabilitySync durabilitySync,
            StoredReadObserver readObserver) {
        try {
            root = properties.filesystemRoot().toAbsolutePath().normalize();
            Files.createDirectories(root);
            if (Files.isSymbolicLink(root)) throw unsafePath();
            enforcePermissions(root, DIRECTORY_PERMISSIONS);
            maximumBlobBytes = properties.maximumBlobBytes();
            reconciliationLimit = properties.reconciliationLimit();
            asyncOperator = AsyncOperator.of("fs", Map.of("root", root.toString()))
                    .layer(RetryLayer.builder().maxTimes(4).jitter(true).build())
                    .layer(new ConcurrentLimitLayer(32));
            operator = asyncOperator.blocking();
            this.durabilitySync = Objects.requireNonNull(
                    durabilitySync,
                    "durabilitySync must not be null");
            this.readObserver = Objects.requireNonNull(
                    readObserver,
                    "readObserver must not be null");
            requireCapabilities();
            scavengeStaging(Instant.now().minus(STAGING_RETENTION));
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw unavailable("files-native-opendal-init-failed");
        }
    }

    @Override public boolean configured() { return true; }

    @Override
    public BlobReceipt putStream(
            BlobScope scope,
            BlobReference reference,
            InputStream source,
            long expectedSize,
            String expectedDigest) {
        requireWithinLimit(expectedSize, "files-native-blob-too-large");
        String requiredDigest = requiredDigest(expectedDigest);
        String key = key(scope, reference);
        String stagingId = UUID.randomUUID().toString();
        String temporary = STAGING_PREFIX + stagingId + ".pending";
        Path spool = null;
        StagingLease stagingLease = null;
        try {
            validateSandbox(scope, reference, true);
            validateStagingSandbox(true);
            stagingLease = acquireStagingLease(stagingId);
            spool = stagingLease.spool();
            Files.createFile(spool);
            enforcePermissions(spool, FILE_PERMISSIONS);

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long transferred;
            try (InputStream digesting = new DigestInputStream(source, digest);
                 OutputStream target = Files.newOutputStream(spool, StandardOpenOption.TRUNCATE_EXISTING)) {
                transferred = BlobStorePort.transferBounded(digesting, target, maximumBlobBytes);
            }
            if (transferred != expectedSize) {
                throw error(HttpStatus.CONFLICT, "files-native-blob-size-mismatch", "The native Files blob size did not match the requested size.");
            }
            String actualDigest = "sha256:" + HexFormat.of().formatHex(digest.digest());
            if (!MessageDigest.isEqual(actualDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII), requiredDigest.getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw error(HttpStatus.CONFLICT, "files-native-blob-digest-mismatch", "The native Files blob digest did not match the requested digest.");
            }
            if (exists(key)) {
                return verifyExisting(scope, key, reference, actualDigest, transferred);
            }

            Path pending = stagingPathForTest(stagingId + ".pending");
            Files.move(spool, pending, StandardCopyOption.ATOMIC_MOVE);
            spool = null;
            enforcePermissions(pending, FILE_PERMISSIONS);
            durabilitySync.force(pending);
            syncStagingDirectoryIfPresent();
            try {
                operator.rename(temporary, key);
            } catch (OpenDALException concurrentPublish) {
                cleanupStaging(temporary);
                if (exists(key)) {
                    return verifyExisting(scope, key, reference, actualDigest, transferred);
                }
                throw concurrentPublish;
            }
            Path target = resolvedPathForTest(scope, reference);
            enforcePermissions(target, FILE_PERMISSIONS);
            durabilitySync.force(target);
            durabilitySync.force(target.getParent());
            syncStagingDirectoryIfPresent();
            return new BlobReceipt(reference, actualDigest, transferred);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException | NoSuchAlgorithmException exception) {
            throw map(exception, "files-native-blob-write-failed");
        } finally {
            boolean stagingClean = cleanupStagingQuietly(temporary);
            if (spool != null) {
                try {
                    if (Files.deleteIfExists(spool)) {
                        syncStagingDirectoryIfPresent();
                    }
                } catch (IOException ignored) {
                    stagingClean = false;
                }
            }
            if (stagingLease != null) {
                stagingLease.close(stagingClean);
            }
        }
    }

    @Override
    public void readStream(BlobScope scope, BlobReference reference, OutputStream target) {
        String key = key(scope, reference);
        try {
            validateSandbox(scope, reference, false);
            if (!exists(key)) throw conflict("files-native-blob-missing");
            long size = statBounded(key);
            readObserver.afterStat(resolvedPathForTest(scope, reference));
            requireWithinLimit(size, "files-native-blob-size-invalid");
            long transferred;
            try (InputStream source = operator.createInputStream(key)) {
                transferred = BlobStorePort.transferBounded(
                        source,
                        contentTarget(target),
                        maximumBlobBytes);
            }
            if (transferred != size) {
                throw error(HttpStatus.CONFLICT, "files-native-blob-size-mismatch", "The native Files blob size did not match its metadata.");
            }
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-read-failed");
        }
    }

    private OutputStream contentTarget(OutputStream target) {
        Objects.requireNonNull(target, "target must not be null");
        return new OutputStream() {
            @Override
            public void write(int value) {
                try {
                    target.write(value);
                } catch (IOException failure) {
                    throw new ContentTargetUnavailableException(failure);
                }
            }

            @Override
            public void write(byte[] value, int offset, int length) {
                try {
                    target.write(value, offset, length);
                } catch (IOException failure) {
                    throw new ContentTargetUnavailableException(failure);
                }
            }
        };
    }

    @Override
    public Optional<BlobReceipt> receipt(BlobScope scope, BlobReference reference) {
        String key = key(scope, reference);
        try {
            validateSandbox(scope, reference, false);
            if (!exists(key)) {
                return Optional.empty();
            }
            BlobReceipt receipt = readReceipt(scope, key, reference);
            Path target = resolvedPathForTest(scope, reference);
            durabilitySync.force(target);
            durabilitySync.force(target.getParent());
            syncStagingDirectoryIfPresent();
            return Optional.of(receipt);
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-read-failed");
        }
    }

    @Override
    public void delete(BlobScope scope, BlobReference reference) {
        try {
            validateSandbox(scope, reference, false);
            operator.delete(key(scope, reference));
            Path parent = resolvedPathForTest(scope, reference).getParent();
            if (Files.isDirectory(parent, LinkOption.NOFOLLOW_LINKS)) {
                durabilitySync.force(parent);
            }
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (IOException | OpenDALException exception) {
            throw map(exception, "files-native-blob-delete-failed");
        }
    }

    @Override
    public List<BlobReference> inventory(BlobScope scope, int limit) {
        if (limit < 1) throw new IllegalArgumentException("inventory limit must be positive");
        String prefix = scopePrefix(scope);
        try {
            scavengeStaging(Instant.now().minus(STAGING_RETENTION));
            Path scopePath = root.resolve(prefix).normalize();
            if (Files.exists(scopePath, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(scopePath)) throw unsafePath();
            ListOptions options = ListOptions.builder().recursive(true).limit((long) limit + 1).build();
            List<BlobReference> values = operator.list(prefix, options).stream()
                    .filter(entry -> entry.getMetadata().isFile())
                    .map(Entry::getPath)
                    .map(path -> path.substring(prefix.length()))
                    .map(BlobReference::new)
                    .toList();
            if (values.size() > limit) throw conflict("files-native-reconciliation-bound-exceeded");
            return values;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return List.of();
            throw map(exception, "files-native-blob-inventory-failed");
        } catch (IOException exception) {
            throw map(exception, "files-native-blob-inventory-failed");
        }
    }

    Path resolvedPathForTest(BlobScope scope, BlobReference reference) {
        Path value = root.resolve(key(scope, reference)).normalize();
        if (!value.startsWith(root)) throw unsafePath();
        return value;
    }

    Path stagingPathForTest(String name) {
        Path value = root.resolve(STAGING_PREFIX).resolve(name).normalize();
        if (!value.startsWith(root.resolve(STAGING_PREFIX).normalize())) throw unsafePath();
        return value;
    }

    private void validateSandbox(BlobScope scope, BlobReference reference, boolean createParents) throws IOException {
        Path target = resolvedPathForTest(scope, reference);
        Path parent = target.getParent();
        validateDirectoryChain(parent, createParents);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(target)) throw unsafePath();
    }

    private void validateStagingSandbox(boolean create) throws IOException {
        Path staging = root.resolve(STAGING_PREFIX).normalize();
        if (!staging.startsWith(root)) throw unsafePath();
        validateDirectoryChain(staging, create);
    }

    private void validateDirectoryChain(Path directory, boolean create) throws IOException {
        Path current = root;
        for (Path segment : root.relativize(directory)) {
            current = current.resolve(segment);
            if (!Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (!create) return;
                Files.createDirectory(current);
            }
            if (Files.isSymbolicLink(current)
                    || !Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) {
                throw unsafePath();
            }
            enforcePermissions(current, DIRECTORY_PERMISSIONS);
            if (create) {
                durabilitySync.force(current);
                durabilitySync.force(current.getParent());
            }
        }
    }

    private void scavengeStaging(Instant staleBefore) throws IOException {
        validateStagingSandbox(false);
        Path staging = root.resolve(STAGING_PREFIX).normalize();
        if (!Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) return;
        List<Path> owners;
        try (var entries = Files.list(staging)) {
            owners = entries
                    .filter(path -> path.getFileName().toString().endsWith(".lock"))
                    .limit(reconciliationLimit)
                    .toList();
        }
        for (Path owner : owners) {
            scavengeStagingOwner(owner, staleBefore);
        }
    }

    private StagingLease acquireStagingLease(String stagingId) throws IOException {
        Path owner = stagingPathForTest(stagingId + ".lock");
        Path spool = stagingPathForTest(stagingId + ".spool");
        FileChannel channel = null;
        FileLock lock = null;
        try {
            Files.createFile(owner);
            enforcePermissions(owner, FILE_PERMISSIONS);
            durabilitySync.force(owner);
            syncStagingDirectoryIfPresent();
            channel = FileChannel.open(owner, StandardOpenOption.WRITE);
            lock = channel.lock();
            return new StagingLease(owner, spool, channel, lock);
        } catch (IOException | RuntimeException failure) {
            if (lock != null) {
                try { lock.release(); } catch (IOException ignored) { }
            }
            if (channel != null) {
                try { channel.close(); } catch (IOException ignored) { }
            }
            try { Files.deleteIfExists(owner); } catch (IOException ignored) { }
            throw failure;
        }
    }

    private void scavengeStagingOwner(Path owner, Instant staleBefore) throws IOException {
        if (Files.isSymbolicLink(owner)
                || !Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS)
                || Files.getLastModifiedTime(owner, LinkOption.NOFOLLOW_LINKS)
                        .toInstant()
                        .isAfter(staleBefore)) {
            return;
        }
        boolean cleaned = false;
        try (FileChannel channel = FileChannel.open(owner, StandardOpenOption.WRITE)) {
            FileLock lock;
            try {
                lock = channel.tryLock();
            } catch (OverlappingFileLockException activeInThisProcess) {
                return;
            }
            if (lock == null) return;
            try (lock) {
                if (Files.getLastModifiedTime(owner, LinkOption.NOFOLLOW_LINKS)
                        .toInstant()
                        .isAfter(staleBefore)) {
                    return;
                }
                String fileName = owner.getFileName().toString();
                String stagingId = fileName.substring(0, fileName.length() - ".lock".length());
                Files.deleteIfExists(stagingPathForTest(stagingId + ".pending"));
                Files.deleteIfExists(stagingPathForTest(stagingId + ".spool"));
                syncStagingDirectoryIfPresent();
                cleaned = true;
            }
        }
        if (cleaned && Files.deleteIfExists(owner)) {
            syncStagingDirectoryIfPresent();
        }
    }

    private void cleanupStaging(String temporary) throws IOException {
        validateStagingSandbox(false);
        operator.delete(temporary);
        syncStagingDirectoryIfPresent();
    }

    private boolean cleanupStagingQuietly(String temporary) {
        try {
            cleanupStaging(temporary);
            return true;
        } catch (RuntimeException | IOException ignored) {
            // A crash-safe, inventory-excluded staging entry remains eligible for bounded scavenging.
            return false;
        }
    }

    private void syncStagingDirectoryIfPresent() throws IOException {
        Path staging = root.resolve(STAGING_PREFIX).normalize();
        if (Files.isDirectory(staging, LinkOption.NOFOLLOW_LINKS)) {
            durabilitySync.force(staging);
        }
    }

    private final class StagingLease {
        private final Path owner;
        private final Path spool;
        private final FileChannel channel;
        private final FileLock lock;

        private StagingLease(
                Path owner,
                Path spool,
                FileChannel channel,
                FileLock lock) {
            this.owner = owner;
            this.spool = spool;
            this.channel = channel;
            this.lock = lock;
        }

        private Path spool() {
            return spool;
        }

        private void close(boolean stagingClean) {
            try {
                lock.release();
            } catch (IOException ignored) {
                stagingClean = false;
            }
            try {
                channel.close();
            } catch (IOException ignored) {
                stagingClean = false;
            }
            if (stagingClean) {
                try {
                    if (Files.deleteIfExists(owner)) {
                        syncStagingDirectoryIfPresent();
                    }
                } catch (IOException ignored) {
                    // The unlocked owner marker remains eligible for bounded scavenging.
                }
            }
        }
    }

    private BlobReceipt verifyExisting(
            BlobScope scope,
            String key,
            BlobReference reference,
            String expectedDigest,
            long expectedSize) throws IOException {
        BlobReceipt existing = readReceipt(scope, key, reference);
        if (existing.size() != expectedSize
                || !constantEquals(existing.digest(), expectedDigest)) {
            throw conflict("files-native-blob-key-collision");
        }
        Path target = resolvedPathForTest(scope, reference);
        enforcePermissions(target, FILE_PERMISSIONS);
        durabilitySync.force(target);
        durabilitySync.force(target.getParent());
        syncStagingDirectoryIfPresent();
        return new BlobReceipt(reference, expectedDigest, expectedSize);
    }

    private BlobReceipt readReceipt(
            BlobScope scope,
            String key,
            BlobReference reference) throws IOException {
        long size = statBounded(key);
        readObserver.afterStat(resolvedPathForTest(scope, reference));
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
        long transferred;
        try (InputStream source = new DigestInputStream(
                operator.createInputStream(key),
                digest)) {
            transferred = BlobStorePort.transferBounded(
                    source,
                    OutputStream.nullOutputStream(),
                    maximumBlobBytes);
        }
        if (transferred != size) {
            throw error(
                    HttpStatus.CONFLICT,
                    "files-native-blob-size-mismatch",
                    "The native Files blob size changed while it was verified.");
        }
        return new BlobReceipt(
                reference,
                "sha256:" + HexFormat.of().formatHex(digest.digest()),
                transferred);
    }

    private long statBounded(String key) {
        long size = operator.stat(key).getContentLength();
        requireWithinLimit(size, "files-native-blob-size-invalid");
        return size;
    }

    private boolean exists(String key) {
        try { return operator.stat(key).isFile(); }
        catch (OpenDALException exception) {
            if (exception.getCode() == OpenDALException.Code.NotFound) return false;
            throw exception;
        }
    }

    private void requireCapabilities() {
        var capability = operator.info.capability;
        if (!capability.read || !capability.write || !capability.delete || !capability.list || !capability.rename) {
            throw unavailable("files-native-opendal-capability-missing");
        }
    }

    private String key(BlobScope scope, BlobReference reference) { return scopePrefix(scope) + reference.value(); }
    private String scopePrefix(BlobScope scope) { return "weave-native/v1/" + hash(scope.organizationRef()) + "/" + hash(scope.spaceRef()) + "/"; }
    private String parent(String key) { int slash = key.lastIndexOf('/'); return slash < 0 ? "" : key.substring(0, slash + 1); }

    private void requireWithinLimit(long size, String code) {
        if (size < 0 || size > maximumBlobBytes) {
            throw error("files-native-blob-too-large".equals(code) ? HttpStatus.PAYLOAD_TOO_LARGE : HttpStatus.CONFLICT, code, "The native Files blob exceeds its configured bound.");
        }
    }

    private String requiredDigest(String value) {
        if (value == null || !value.matches("sha256:[a-f0-9]{64}")) throw new IllegalArgumentException("expected digest must be sha256");
        return value;
    }

    static String digest(byte[] bytes) {
        try { return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private String hash(String value) { return digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)).substring("sha256:".length()); }

    private void enforcePermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try { Files.setPosixFilePermissions(path, permissions); } catch (UnsupportedOperationException ignored) { }
    }

    private boolean constantEquals(String first, String second) {
        return MessageDigest.isEqual(
                first.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                second.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    @FunctionalInterface
    interface DurabilitySync {
        void force(Path path) throws IOException;

        static DurabilitySync system() {
            return path -> {
                try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
                    channel.force(true);
                }
            };
        }
    }

    @FunctionalInterface
    interface StoredReadObserver {
        void afterStat(Path path) throws IOException;
    }

    private ApiErrorException map(Throwable error, String fallback) {
        if (error instanceof OpenDALException opendal) {
            return switch (opendal.getCode()) {
                case NotFound -> conflict("files-native-blob-missing");
                case PermissionDenied -> error(HttpStatus.FORBIDDEN, "files-native-blob-permission-denied", "The native Files blob store denied the operation.");
                case AlreadyExists, ConditionNotMatch -> conflict("files-native-blob-key-collision");
                case RateLimited -> error(HttpStatus.SERVICE_UNAVAILABLE, "files-native-blob-rate-limited", "The native Files blob store is temporarily rate limited.");
                default -> unavailable(fallback);
            };
        }
        return unavailable(fallback);
    }

    private ApiErrorException unsafePath() { return error(HttpStatus.CONFLICT, "files-native-path-containment-failed", "The native Files storage path failed containment validation."); }
    private ApiErrorException conflict(String code) { return error(HttpStatus.CONFLICT, code, "The native Files blob state is inconsistent."); }
    private ApiErrorException unavailable(String code) { return error(HttpStatus.SERVICE_UNAVAILABLE, code, "The native Files blob store is temporarily unavailable."); }
    private ApiErrorException error(HttpStatus status, String code, String message) { return new ApiErrorException(status, code, message, Map.of("module", "files", "adapter", "weave-native", "diagnosticsRedacted", true)); }

    @PreDestroy
    void closeOperator() {
        try { operator.close(); } finally { asyncOperator.close(); }
    }
}
