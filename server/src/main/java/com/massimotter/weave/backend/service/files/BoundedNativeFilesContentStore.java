package com.massimotter.weave.backend.service.files;

import com.massimotter.weave.backend.config.WeaveNativeFilesProperties;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.ContentProfile;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Egress;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.NativeFilesContentStore;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.ReplayableFileContent.StreamFactory;
import com.massimotter.weave.backend.files.port.VerifiedFileRead;
import com.massimotter.weave.backend.files.port.BlobStorePort.ContentTargetUnavailableException;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority.Authority;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;

/**
 * Generation-fenced private storage for bounded native Files ingress and verified egress.
 *
 * <p>The store deliberately has no Spring annotations. Composition must supply an authority gate
 * that has validated the exact relational authority row, schema receipt, root marker, volume, and
 * generation accepted by readiness. Every entry point repeats that gate and marker check before it
 * reads, publishes, reopens, or scavenges private content.</p>
 */
public final class BoundedNativeFilesContentStore implements NativeFilesContentStore {

    public static final int TRANSFER_BUFFER_BYTES = ReplayableFileContent.TRANSFER_BUFFER_BYTES;
    public static final int DEFAULT_INGRESS_CONCURRENCY = 4;
    public static final int DEFAULT_EGRESS_CONCURRENCY = 4;

    private static final String PRIVATE_NAMESPACE = ".weave-files-private-content-v1";
    private static final String UNBOUND_DIRECTORY = "unbound";
    private static final String BOUND_DIRECTORY = "bound";
    private static final String EGRESS_DIRECTORY = "egress";
    private static final String CAPACITY_LOCK_FILE = "capacity.lock";
    private static final String OWNER_LOCK_FILE = "owner.lock";
    private static final String CONTENT_FILE = "content.bin";
    private static final String STATE_FILE = "state.meta";
    private static final String STATE_FORMAT = "weave.native-files-private-content/v1";
    private static final long MAXIMUM_STATE_BYTES = 32L * 1024L;
    private static final long MAXIMUM_OPERATION_REF_BYTES = 8L * 1024L;
    private static final String NONE = "-";
    private static final Map<String, Object> REDACTED_DETAILS = Map.of(
            "module", "files",
            "diagnosticsRedacted", true);

    private final Path authorityRoot;
    private final Path privateNamespaceRoot;
    private final Path generationRoot;
    private final Path unboundRoot;
    private final Path boundRoot;
    private final Path egressRoot;
    private final Path capacityLockPath;
    private final ContentProfile profile;
    private final long ingressCapacityBytes;
    private final long egressCapacityBytes;
    private final AuthorityGate authorityGate;
    private final ProtectionProbe protectionProbe;
    private final Clock clock;
    private final Authority pinnedAuthority;
    private final String authorityRowDigest;
    private final Object scavengeCursorMonitor = new Object();
    private ScavengeCandidateCursor scavengeCursor;

    public BoundedNativeFilesContentStore(
            WeaveNativeFilesProperties properties,
            AuthorityGate authorityGate,
            ProtectionProbe protectionProbe) {
        this(
                Objects.requireNonNull(properties, "properties must not be null").filesystemRoot(),
                properties.maximumBlobBytes(),
                DEFAULT_INGRESS_CONCURRENCY,
                DEFAULT_EGRESS_CONCURRENCY,
                multipliedCapacity(properties.maximumBlobBytes(), DEFAULT_INGRESS_CONCURRENCY),
                multipliedCapacity(properties.maximumBlobBytes(), DEFAULT_EGRESS_CONCURRENCY),
                authorityGate,
                protectionProbe,
                Clock.systemUTC());
    }

    /** Full constructor used by explicit composition and deterministic focused tests. */
    public BoundedNativeFilesContentStore(
            Path authorityRoot,
            long maximumContentBytes,
            int maximumIngressConcurrency,
            int maximumEgressConcurrency,
            long ingressCapacityBytes,
            long egressCapacityBytes,
            AuthorityGate authorityGate,
            ProtectionProbe protectionProbe,
            Clock clock) {
        this.authorityRoot = Objects.requireNonNull(authorityRoot, "authorityRoot must not be null")
                .toAbsolutePath()
                .normalize();
        this.profile = new ContentProfile(
                maximumContentBytes,
                TRANSFER_BUFFER_BYTES,
                maximumIngressConcurrency,
                maximumEgressConcurrency);
        if (maximumContentBytes > FilesMutationPlan.JSON_SAFE_INTEGER_MAX - 1) {
            throw new IllegalArgumentException("maximumContentBytes is outside the accepted range");
        }
        if (ingressCapacityBytes < maximumContentBytes
                || egressCapacityBytes < maximumContentBytes) {
            throw new IllegalArgumentException("private content capacity is below one transfer");
        }
        this.ingressCapacityBytes = ingressCapacityBytes;
        this.egressCapacityBytes = egressCapacityBytes;
        this.authorityGate = Objects.requireNonNull(authorityGate, "authorityGate must not be null");
        this.protectionProbe = Objects.requireNonNull(
                protectionProbe, "protectionProbe must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        try {
            this.pinnedAuthority = requireAuthorityAtConstruction();
            this.authorityRowDigest = NativeFilesVolumeAuthority.rowDigest(pinnedAuthority);
            this.privateNamespaceRoot = contained(this.authorityRoot.resolve(PRIVATE_NAMESPACE));
            Path volumeRoot = contained(privateNamespaceRoot.resolve(pinnedAuthority.volumeRef()));
            this.generationRoot = contained(volumeRoot.resolve(pinnedAuthority.generationRef()));
            this.unboundRoot = contained(generationRoot.resolve(UNBOUND_DIRECTORY));
            this.boundRoot = contained(generationRoot.resolve(BOUND_DIRECTORY));
            this.egressRoot = contained(generationRoot.resolve(EGRESS_DIRECTORY));
            initializeDirectory(privateNamespaceRoot);
            initializeDirectory(volumeRoot);
            initializeDirectory(generationRoot);
            initializeDirectory(unboundRoot);
            initializeDirectory(boundRoot);
            initializeDirectory(egressRoot);
            this.capacityLockPath = contained(generationRoot.resolve(CAPACITY_LOCK_FILE));
            initializePrivateFile(capacityLockPath, new byte[0]);
            forceDirectory(generationRoot);
        } catch (Exception failure) {
            throw new IllegalStateException(
                    "native Files private content storage could not be initialized", failure);
        }
    }

    @Override
    public ContentProfile contentProfile() {
        return profile;
    }

    @Override
    public void requireStreamingReady() {
        requireCurrentAuthority();
    }

    @Override
    public Ingress receive(Long declaredLength, String mediaType, StreamFactory requestBody) {
        requireCurrentAuthority();
        validateIngressArguments(declaredLength, mediaType, requestBody);
        if (declaredLength != null && declaredLength > profile.maximumContentBytes()) {
            throw contentTooLarge();
        }
        long reservation = declaredLength == null
                ? profile.maximumContentBytes()
                : declaredLength;
        LockedObject object = reserve(
                ObjectKind.INGRESS,
                reservation,
                StatePhase.ACTIVE_INGRESS,
                unboundRoot,
                storageUnavailable());
        try {
            Received received = receiveInto(object, declaredLength, mediaType, requestBody);
            State sealed = object.state().sealedIngress(
                    received.sizeBytes(), received.digest(), mediaType);
            writeState(object.directory(), sealed);
            object.state(sealed);
            return new IngressHandle(object, false);
        } catch (IOException failure) {
            deleteAndCloseQuietly(object);
            throw storageUnavailable(failure);
        } catch (RuntimeException failure) {
            deleteAndCloseQuietly(object);
            throw failure;
        }
    }

    @Override
    public Egress verify(VerifiedFileRead read) {
        Objects.requireNonNull(read, "read must not be null");
        requireCurrentAuthority();
        long expectedSize = read.headers().contentLength();
        if (expectedSize > profile.maximumContentBytes()) {
            throw capacityUnavailable();
        }
        LockedObject object = reserve(
                ObjectKind.EGRESS,
                expectedSize,
                StatePhase.ACTIVE_EGRESS,
                egressRoot,
                capacityUnavailable());
        MessageDigest digest = sha256();
        try (FileChannel output = openContentForWrite(object.directory())) {
            ExactBoundedOutputStream bounded = new ExactBoundedOutputStream(
                    output, expectedSize, digest);
            try {
                read.transferTo(bounded);
                bounded.requireComplete();
            } catch (ContentTargetUnavailableException failure) {
                throw capacityUnavailable(failure);
            } catch (PrivateStorageIOException failure) {
                throw capacityUnavailable(failure);
            } catch (RuntimeException | IOException failure) {
                throw contentIntegrityUnavailable(failure);
            }
            output.force(true);
            String observedDigest = "sha256:" + HexFormat.of().formatHex(digest.digest());
            State sealed = object.state().sealedEgress(
                    expectedSize, observedDigest, read.headers().contentType());
            writeState(object.directory(), sealed);
            object.state(sealed);
            forceDirectory(object.directory());
            return new EgressHandle(object);
        } catch (ApiErrorException failure) {
            deleteAndCloseQuietly(object);
            throw failure;
        } catch (IOException failure) {
            deleteAndCloseQuietly(object);
            throw capacityUnavailable(failure);
        } catch (RuntimeException failure) {
            deleteAndCloseQuietly(object);
            throw contentIntegrityUnavailable(failure);
        }
    }

    /** Reopens the one exact same-generation retained ingress object for retry/recovery. */
    @Override
    public Ingress reopen(String operationRef) {
        String requiredOperationRef = requireOperationRef(operationRef);
        requireCurrentAuthority();
        Path directory = boundPath(requiredOperationRef);
        LockedObject object;
        try {
            object = lockExisting(directory);
            if (object == null) {
                throw contentIntegrityUnavailable(null);
            }
            State state = object.state();
            if (state.kind() != ObjectKind.INGRESS
                    || state.phase() != StatePhase.BOUND_INGRESS
                    || !requiredOperationRef.equals(state.operationRef())) {
                throw contentIntegrityUnavailable(null);
            }
            verifyPrivateContent(object.directory(), state);
            return new IngressHandle(object, true);
        } catch (ApiErrorException failure) {
            throw failure;
        } catch (Exception failure) {
            throw contentIntegrityUnavailable(failure);
        }
    }

    /**
     * Removes a terminal retained ingress only if an exact relational recheck proves it is no
     * longer protected. Probe unavailability fails closed and retains the object.
     */
    @Override
    public boolean remove(String operationRef) {
        String requiredOperationRef = requireOperationRef(operationRef);
        try {
            requireCurrentAuthority();
            return withCapacityLock(() -> {
                LockedObject object = lockExisting(boundPath(requiredOperationRef));
                if (object == null) {
                    return false;
                }
                try {
                    State state = object.state();
                    if (!requiredOperationRef.equals(state.operationRef())
                            || state.phase() != StatePhase.BOUND_INGRESS) {
                        return false;
                    }
                    Protection protection = safeProbe(requiredOperationRef);
                    if (protection != Protection.UNPROTECTED) {
                        return false;
                    }
                    deleteObject(object.directory());
                    return true;
                } finally {
                    object.close();
                }
            });
        } catch (Exception unavailable) {
            return false;
        }
    }

    /** Age-fenced, bounded, lock-aware cleanup for crash-left private content. */
    public ScavengeReport scavenge(Instant olderThan, int limit) {
        Objects.requireNonNull(olderThan, "olderThan must not be null");
        if (limit < 1) {
            throw new IllegalArgumentException("scavenge limit must be positive");
        }
        requireCurrentAuthority();
        MutableScavengeReport report = new MutableScavengeReport();
        synchronized (scavengeCursorMonitor) {
            while (report.examined < limit) {
                Path candidate;
                try {
                    candidate = nextScavengeCandidate();
                } catch (IOException unsafe) {
                    resetScavengeCursor();
                    throw streamingNotSupported(unsafe);
                }
                if (candidate == null) {
                    resetScavengeCursor();
                    break;
                }
                report.examined++;
                try {
                    withCapacityLock(() -> {
                        scavengeCandidate(candidate, olderThan, report);
                        return null;
                    });
                } catch (Exception unavailable) {
                    report.unavailableChecks++;
                }
            }
        }
        return report.freeze();
    }

    @Override
    public void scavengeBounded(Instant olderThan, int limit) {
        scavenge(olderThan, limit);
    }

    private void scavengeCandidate(
            Path candidate, Instant olderThan, MutableScavengeReport report) throws IOException {
        directoryAttributes(candidate);
        Path owner = contained(candidate.resolve(OWNER_LOCK_FILE));
        if (!Files.exists(owner, LinkOption.NOFOLLOW_LINKS)) {
            if (candidate.getParent().equals(boundRoot)) {
                report.unavailableChecks++;
                return;
            }
            BasicFileAttributes rechecked = directoryAttributes(candidate);
            if (!rechecked.lastModifiedTime().toInstant().isBefore(olderThan)) {
                report.lockedOrChanged++;
                return;
            }
            requireCurrentAuthority();
            deleteObject(candidate);
            report.deleted++;
            return;
        }
        LockedObject object = lockOwner(candidate);
        if (object == null) {
            report.lockedOrChanged++;
            return;
        }
        try {
            State state;
            try {
                state = readState(candidate);
                object.state(state);
            } catch (IOException | RuntimeException corruptState) {
                if (candidate.getParent().equals(boundRoot)) {
                    report.unavailableChecks++;
                    return;
                }
                BasicFileAttributes rechecked = directoryAttributes(candidate);
                if (!rechecked.lastModifiedTime().toInstant().isBefore(olderThan)) {
                    report.lockedOrChanged++;
                    return;
                }
                requireCurrentAuthority();
                deleteObject(candidate);
                report.deleted++;
                return;
            }
            if (!state.createdAt().isBefore(olderThan)) {
                report.young++;
                return;
            }
            requireCurrentAuthority();
            if (state.phase() == StatePhase.BOUND_INGRESS) {
                Protection protection = safeProbe(state.operationRef());
                if (protection == Protection.PROTECTED) {
                    report.protectedObjects++;
                    return;
                }
                if (protection == Protection.UNAVAILABLE) {
                    report.unavailableChecks++;
                    return;
                }
            }
            deleteObject(object.directory());
            report.deleted++;
        } finally {
            object.close();
        }
    }

    public interface AuthorityGate {
        /** Returns the exact authority whose row, receipt, marker, volume, and generation are ready. */
        Authority requireValidated();
    }

    @FunctionalInterface
    public interface ProtectionProbe {
        Protection probe(String operationRef);
    }

    public enum Protection {
        PROTECTED,
        UNPROTECTED,
        UNAVAILABLE
    }

    public record ScavengeReport(
            int examined,
            int deleted,
            int protectedObjects,
            int unavailableChecks,
            int young,
            int lockedOrChanged) {
    }

    private Received receiveInto(
            LockedObject object,
            Long declaredLength,
            String mediaType,
            StreamFactory requestBody) {
        MessageDigest digest = sha256();
        long maximumRead = declaredLength == null
                ? profile.maximumContentBytes() + 1
                : declaredLength + 1;
        long total = 0;
        byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
        try (InputStream source = openRequestBody(requestBody)) {
            FileChannel openedOutput;
            try {
                openedOutput = openContentForWrite(object.directory());
            } catch (IOException | RuntimeException failure) {
                throw storageUnavailable(failure);
            }
            try (FileChannel output = openedOutput) {
                while (total < maximumRead) {
                    int wanted = (int) Math.min((long) buffer.length, maximumRead - total);
                    int read;
                    try {
                        read = source.read(buffer, 0, wanted);
                    } catch (IOException failure) {
                        throw unreadableIngress(failure);
                    }
                    if (read < 0) {
                        break;
                    }
                    if (read == 0) {
                        continue;
                    }
                    long acceptedLimit = declaredLength == null
                            ? profile.maximumContentBytes()
                            : declaredLength;
                    long accepted = Math.min((long) read, Math.max(0L, acceptedLimit - total));
                    if (accepted > 0) {
                        try {
                            writeFully(output, buffer, 0, Math.toIntExact(accepted));
                        } catch (IOException failure) {
                            throw storageUnavailable(failure);
                        }
                        digest.update(buffer, 0, Math.toIntExact(accepted));
                    }
                    total += read;
                }
                if (declaredLength == null && total > profile.maximumContentBytes()) {
                    throw contentTooLarge();
                }
                if (declaredLength != null && total != declaredLength) {
                    throw sizeMismatch();
                }
                try {
                    output.force(true);
                    forceDirectory(object.directory());
                } catch (IOException failure) {
                    throw storageUnavailable(failure);
                }
            } catch (ApiErrorException failure) {
                throw failure;
            } catch (IOException failure) {
                // Only the private output close can reach here; request-source reads are mapped in
                // the loop and request-source close is mapped by the outer resource boundary.
                throw storageUnavailable(failure);
            }
        } catch (ApiErrorException failure) {
            throw failure;
        } catch (IOException failure) {
            throw unreadableIngress(failure);
        }
        return new Received(
                total,
                "sha256:" + HexFormat.of().formatHex(digest.digest()),
                mediaType);
    }

    private InputStream openRequestBody(StreamFactory requestBody) {
        try {
            InputStream source = requestBody.openStream();
            if (source == null) {
                throw unreadableIngress(null);
            }
            return source;
        } catch (ApiErrorException failure) {
            throw failure;
        } catch (IOException | RuntimeException failure) {
            throw unreadableIngress(failure);
        }
    }

    private LockedObject reserve(
            ObjectKind kind,
            long reservedBytes,
            StatePhase phase,
            Path parent,
            ApiErrorException storageFailure) {
        try {
            return withCapacityLock(() -> {
                CapacityUsage usage = capacityUsage();
                long capacity = kind == ObjectKind.INGRESS
                        ? ingressCapacityBytes
                        : egressCapacityBytes;
                int concurrency = kind == ObjectKind.INGRESS
                        ? profile.maximumIngressConcurrency()
                        : profile.maximumEgressConcurrency();
                long used = kind == ObjectKind.INGRESS
                        ? usage.ingressBytes()
                        : usage.egressBytes();
                int active = kind == ObjectKind.INGRESS
                        ? usage.activeIngress()
                        : usage.activeEgress();
                if (active >= concurrency) {
                    throw capacityUnavailable();
                }
                if (exceeds(used, reservedBytes, capacity)) {
                    throw kind == ObjectKind.INGRESS
                            ? storageUnavailable()
                            : capacityUnavailable();
                }
                Path directory = contained(parent.resolve(UUID.randomUUID().toString()));
                createPrivateDirectory(directory);
                try {
                    initializePrivateFile(
                            directory.resolve(OWNER_LOCK_FILE),
                            UUID.randomUUID().toString().getBytes(StandardCharsets.US_ASCII));
                    State state = State.active(
                            kind,
                            phase,
                            reservedBytes,
                            Instant.now(clock),
                            pinnedAuthority,
                            authorityRowDigest);
                    writeState(directory, state);
                    forceDirectory(directory);
                    forceDirectory(parent);
                    LockedObject locked = lockExisting(directory);
                    if (locked == null) {
                        throw new IOException("private content owner lock was unavailable");
                    }
                    return locked;
                } catch (Exception failure) {
                    deleteObjectQuietly(directory);
                    throw failure;
                }
            });
        } catch (ApiErrorException failure) {
            throw failure;
        } catch (CapacityLockUnavailable failure) {
            throw capacityUnavailable();
        } catch (Exception failure) {
            if (storageFailure.status() == HttpStatus.INSUFFICIENT_STORAGE) {
                throw storageUnavailable(failure);
            }
            throw capacityUnavailable(failure);
        }
    }

    private CapacityUsage capacityUsage() throws IOException {
        long ingress = 0;
        long egress = 0;
        int activeIngress = 0;
        int activeEgress = 0;
        for (Path root : List.of(unboundRoot, boundRoot, egressRoot)) {
            for (Path candidate : safeChildren(root)) {
                State state = readState(candidate);
                if (state.kind() == ObjectKind.INGRESS) {
                    ingress = addExactCapacity(ingress, state.reservedBytes());
                    if (state.phase() == StatePhase.ACTIVE_INGRESS) {
                        activeIngress++;
                    }
                } else {
                    egress = addExactCapacity(egress, state.reservedBytes());
                    if (state.phase() == StatePhase.ACTIVE_EGRESS
                            || state.phase() == StatePhase.SEALED_EGRESS) {
                        activeEgress++;
                    }
                }
            }
        }
        return new CapacityUsage(ingress, egress, activeIngress, activeEgress);
    }

    private <T> T withCapacityLock(IoSupplier<T> action) throws Exception {
        requireCurrentAuthority();
        ensurePrivateRegularFile(capacityLockPath);
        try (FileChannel channel = FileChannel.open(
                        capacityLockPath,
                        Set.of(
                                StandardOpenOption.READ,
                                StandardOpenOption.WRITE,
                                LinkOption.NOFOLLOW_LINKS));
                FileLock ignored = tryExclusiveLock(channel)) {
            if (ignored == null) {
                throw new CapacityLockUnavailable();
            }
            return action.get();
        }
    }

    private LockedObject lockExisting(Path directory) throws IOException {
        LockedObject locked = lockOwner(directory);
        if (locked == null) {
            return null;
        }
        try {
            State state = readState(directory);
            validateStateAuthority(state);
            locked.state(state);
            return locked;
        } catch (IOException | RuntimeException failure) {
            locked.close();
            throw failure;
        }
    }

    private LockedObject lockOwner(Path directory) throws IOException {
        if (!isSafeDirectory(directory)) {
            return null;
        }
        Path owner = contained(directory.resolve(OWNER_LOCK_FILE));
        ensurePrivateRegularFile(owner);
        FileChannel channel = FileChannel.open(
                owner,
                Set.of(StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS));
        FileLock lock = null;
        try {
            lock = tryExclusiveLock(channel);
            if (lock == null) {
                channel.close();
                return null;
            }
            return new LockedObject(directory, channel, lock, null);
        } catch (IOException | RuntimeException failure) {
            if (lock != null) {
                lock.close();
            }
            channel.close();
            throw failure;
        }
    }

    private FileLock tryExclusiveLock(FileChannel channel) throws IOException {
        try {
            return channel.tryLock();
        } catch (OverlappingFileLockException overlap) {
            return null;
        }
    }

    private void bind(IngressHandle handle, String operationRef) throws IOException {
        LockedObject current = handle.object();
        State currentState = current.state();
        if (handle.bound()) {
            if (!operationRef.equals(currentState.operationRef())) {
                throw contentIntegrityUnavailable(null);
            }
            return;
        }
        Path target = boundPath(operationRef);
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            adoptRetained(handle, operationRef, target);
            return;
        }
        State bound = currentState.bound(operationRef);
        writeState(current.directory(), bound);
        current.state(bound);
        try {
            Files.move(current.directory(), target, StandardCopyOption.ATOMIC_MOVE);
        } catch (FileAlreadyExistsException concurrentWinner) {
            adoptRetained(handle, operationRef, target);
            return;
        } catch (AtomicMoveNotSupportedException unavailable) {
            throw new IOException("private ingress binding requires an atomic directory move", unavailable);
        }
        forceDirectory(unboundRoot);
        forceDirectory(boundRoot);
        current.directory(target);
        handle.replace(current, true);
    }

    private void adoptRetained(IngressHandle handle, String operationRef, Path target)
            throws IOException {
        LockedObject current = handle.object();
        LockedObject retained;
        try {
            retained = lockExisting(target);
        } catch (IOException | RuntimeException corruptRetained) {
            throw contentIntegrityUnavailable(corruptRetained);
        }
        if (retained == null) {
            throw capacityUnavailable();
        }
        try {
            State currentState = current.state();
            State retainedState = retained.state();
            if (!operationRef.equals(retainedState.operationRef())
                    || retainedState.phase() != StatePhase.BOUND_INGRESS
                    || !retainedState.sameRepresentation(currentState)) {
                throw contentIntegrityUnavailable(null);
            }
            verifyPrivateContent(retained.directory(), retainedState);
            deleteObject(current.directory());
            current.close();
            handle.replace(retained, true);
            retained = null;
        } finally {
            if (retained != null) {
                retained.close();
            }
        }
    }

    private void handleTransactionFailure(IngressHandle handle) {
        LockedObject object = handle.object();
        State state = object.state();
        if (!handle.bound() || state.operationRef() == null) {
            deleteAndCloseQuietly(object);
            handle.markClosed();
            return;
        }
        if (safeProbe(state.operationRef()) == Protection.UNPROTECTED) {
            deleteAndCloseQuietly(object);
            handle.markClosed();
        }
    }

    private Protection safeProbe(String operationRef) {
        if (operationRef == null) {
            return Protection.UNAVAILABLE;
        }
        try {
            return Objects.requireNonNullElse(
                    protectionProbe.probe(operationRef), Protection.UNAVAILABLE);
        } catch (RuntimeException unavailable) {
            return Protection.UNAVAILABLE;
        }
    }

    private ReplayableFileContent contentFor(IngressHandle handle) {
        State state = handle.object().state();
        return new ReplayableFileContent(
                state.sizeBytes(),
                state.digest(),
                state.mediaType(),
                () -> {
                    if (handle.closed()) {
                        throw new IOException("private ingress handle is closed");
                    }
                    requireCurrentAuthority();
                    State current = handle.object().state();
                    if (!current.sameRepresentation(state)) {
                        throw new IOException("private ingress descriptor changed");
                    }
                    return openContentForRead(handle.object().directory(), current);
                });
    }

    private InputStream openContentForRead(Path directory, State state) throws IOException {
        Path content = contained(directory.resolve(CONTENT_FILE));
        BasicFileAttributes attributes = regularFileAttributes(content);
        if (attributes.size() != state.sizeBytes()) {
            throw new IOException("private content size changed");
        }
        return Files.newInputStream(
                content, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
    }

    private FileChannel openContentForWrite(Path directory) throws IOException {
        Path content = contained(directory.resolve(CONTENT_FILE));
        if (Files.exists(content, LinkOption.NOFOLLOW_LINKS)) {
            ensurePrivateRegularFile(content);
        }
        FileChannel channel = FileChannel.open(
                content,
                Set.of(
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE,
                        LinkOption.NOFOLLOW_LINKS));
        setPrivateFilePermissions(content);
        return channel;
    }

    private void verifyPrivateContent(Path directory, State state) throws IOException {
        MessageDigest digest = sha256();
        long observed = 0;
        byte[] buffer = new byte[TRANSFER_BUFFER_BYTES];
        try (InputStream input = openContentForRead(directory, state)) {
            while (true) {
                int wanted = (int) Math.min(
                        (long) buffer.length,
                        Math.max(1L, state.sizeBytes() + 1 - observed));
                int read = input.read(buffer, 0, wanted);
                if (read < 0) {
                    break;
                }
                if (read == 0) {
                    continue;
                }
                observed += read;
                if (observed > state.sizeBytes()) {
                    throw new IOException("private content is oversized");
                }
                digest.update(buffer, 0, read);
            }
        }
        String observedDigest = "sha256:" + HexFormat.of().formatHex(digest.digest());
        if (observed != state.sizeBytes()
                || !MessageDigest.isEqual(
                        observedDigest.getBytes(StandardCharsets.US_ASCII),
                        state.digest().getBytes(StandardCharsets.US_ASCII))) {
            throw new IOException("private content integrity changed");
        }
    }

    private void validateIngressArguments(
            Long declaredLength, String mediaType, StreamFactory requestBody) {
        if (declaredLength != null && declaredLength < 0) {
            throw invalidIngress();
        }
        if (mediaType == null
                || mediaType.isBlank()
                || !mediaType.equals(mediaType.trim())
                || mediaType.indexOf('\r') >= 0
                || mediaType.indexOf('\n') >= 0
                || requestBody == null) {
            throw invalidIngress();
        }
    }

    private Authority requireAuthorityAtConstruction() throws Exception {
        Authority authority = Objects.requireNonNull(
                authorityGate.requireValidated(), "authority gate returned no authority");
        NativeFilesVolumeAuthority.validateMarker(authorityRoot, authority);
        return authority;
    }

    private void requireCurrentAuthority() {
        try {
            Authority current = Objects.requireNonNull(
                    authorityGate.requireValidated(), "authority gate returned no authority");
            if (!pinnedAuthority.equals(current)) {
                throw new IllegalStateException("native Files volume generation changed");
            }
            NativeFilesVolumeAuthority.validateMarker(authorityRoot, current);
            requireSafeDirectory(authorityRoot);
            requireSafeDirectory(generationRoot);
        } catch (Exception failure) {
            throw streamingNotSupported(failure);
        }
    }

    private void validateStateAuthority(State state) {
        if (!authorityRowDigest.equals(state.authorityRowDigest())
                || !pinnedAuthority.volumeRef().equals(state.volumeRef())
                || !pinnedAuthority.generationRef().equals(state.generationRef())) {
            throw new IllegalStateException("private content belongs to another authority generation");
        }
    }

    private Path boundPath(String operationRef) {
        return contained(boundRoot.resolve(hexSha256(operationRef)));
    }

    private String requireOperationRef(String operationRef) {
        if (operationRef == null
                || operationRef.isBlank()
                || !operationRef.equals(operationRef.trim())
                || operationRef.indexOf('\r') >= 0
                || operationRef.indexOf('\n') >= 0
                || operationRef.getBytes(StandardCharsets.UTF_8).length > MAXIMUM_OPERATION_REF_BYTES) {
            throw new IllegalArgumentException("operationRef is invalid");
        }
        return operationRef;
    }

    private String hexSha256(String value) {
        return HexFormat.of().formatHex(sha256().digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private void initializeDirectory(Path directory) throws IOException {
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            requireSafeDirectory(directory);
            return;
        }
        createPrivateDirectory(directory);
    }

    private void createPrivateDirectory(Path directory) throws IOException {
        Path parent = directory.getParent();
        requireSafeDirectory(parent);
        Files.createDirectory(directory);
        setPrivateDirectoryPermissions(directory);
        forceDirectory(parent);
    }

    private void initializePrivateFile(Path path, byte[] content) throws IOException {
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            ensurePrivateRegularFile(path);
            return;
        }
        Files.write(path, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        setPrivateFilePermissions(path);
        try (FileChannel file = FileChannel.open(path, StandardOpenOption.WRITE)) {
            file.force(true);
        }
        forceDirectory(path.getParent());
    }

    private void writeState(Path directory, State state) throws IOException {
        validateStateAuthority(state);
        byte[] serialized = state.serialize();
        if (serialized.length > MAXIMUM_STATE_BYTES) {
            throw new IOException("private content state is oversized");
        }
        Path temporary = contained(directory.resolve("state-" + UUID.randomUUID() + ".tmp"));
        Path target = contained(directory.resolve(STATE_FILE));
        try {
            Files.write(temporary, serialized, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            setPrivateFilePermissions(temporary);
            try (FileChannel file = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
                file.force(true);
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unavailable) {
                throw new IOException("private content state requires an atomic file move", unavailable);
            }
            forceDirectory(directory);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private State readState(Path directory) throws IOException {
        Path path = contained(directory.resolve(STATE_FILE));
        BasicFileAttributes attributes = regularFileAttributes(path);
        if (attributes.size() < 1 || attributes.size() > MAXIMUM_STATE_BYTES) {
            throw new IOException("private content state is missing or oversized");
        }
        ByteBuffer bounded = ByteBuffer.allocate(Math.toIntExact(MAXIMUM_STATE_BYTES + 1));
        try (FileChannel input = FileChannel.open(
                path, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS))) {
            while (bounded.hasRemaining()) {
                int read = input.read(bounded);
                if (read < 0) {
                    break;
                }
            }
        }
        if (bounded.position() > MAXIMUM_STATE_BYTES) {
            throw new IOException("private content state is oversized");
        }
        byte[] value = new byte[bounded.position()];
        bounded.flip();
        bounded.get(value);
        State state = State.parse(value);
        validateStateAuthority(state);
        return state;
    }

    private List<Path> safeChildren(Path root) throws IOException {
        requireSafeDirectory(root);
        List<Path> children = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
            for (Path child : stream) {
                Path contained = contained(child);
                if (!isSafeDirectory(contained)) {
                    throw new IOException("private content namespace contains an unsafe entry");
                }
                children.add(contained);
            }
        }
        return children;
    }

    private Path nextScavengeCandidate() throws IOException {
        if (scavengeCursor == null) {
            scavengeCursor = new ScavengeCandidateCursor(
                    List.of(unboundRoot, boundRoot, egressRoot));
        }
        return scavengeCursor.next();
    }

    private void resetScavengeCursor() {
        if (scavengeCursor == null) {
            return;
        }
        try {
            scavengeCursor.close();
        } catch (IOException ignored) {
            // The next pass revalidates and reopens the generation-fenced namespace.
        } finally {
            scavengeCursor = null;
        }
    }

    private void deleteObject(Path directory) throws IOException {
        if (!isSafeDirectory(directory)) {
            return;
        }
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(directory)) {
            for (Path entry : entries) {
                Path safe = contained(entry);
                BasicFileAttributes attributes = Files.readAttributes(
                        safe, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
                    throw new IOException("private content object contains an unsafe entry");
                }
                Files.delete(safe);
            }
        }
        Files.delete(directory);
        forceDirectory(directory.getParent());
    }

    private void deleteObjectQuietly(Path directory) {
        try {
            deleteObject(directory);
        } catch (IOException ignored) {
            // Failing closed leaves a private object for the bounded scavenger.
        }
    }

    private void deleteAndCloseQuietly(LockedObject object) {
        if (object == null) {
            return;
        }
        try {
            deleteObject(object.directory());
        } catch (IOException ignored) {
            // Failing closed leaves a private object for the bounded scavenger.
        } finally {
            object.close();
        }
    }

    private Path contained(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(authorityRoot) || normalized.equals(authorityRoot)) {
            throw new IllegalStateException("private Files content path escaped its authority root");
        }
        return normalized;
    }

    private boolean isSafeDirectory(Path path) {
        try {
            requireSafeDirectory(path);
            return true;
        } catch (IOException unsafe) {
            return false;
        }
    }

    private void requireSafeDirectory(Path path) throws IOException {
        if (path == null) {
            throw new IOException("private Files content directory is unavailable or unsafe");
        }
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(authorityRoot)) {
            throw new IOException("private Files content directory escaped its authority root");
        }
        Path cursor = authorityRoot;
        requireOneSafeDirectory(cursor);
        for (Path segment : authorityRoot.relativize(normalized)) {
            cursor = cursor.resolve(segment);
            requireOneSafeDirectory(cursor);
        }
    }

    private void requireOneSafeDirectory(Path directory) throws IOException {
        if (Files.isSymbolicLink(directory)
                || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("private Files content directory is unavailable or unsafe");
        }
        if (privateNamespaceRoot != null && directory.startsWith(privateNamespaceRoot)) {
            requireOwnerOnlyPermissions(directory, true);
        }
    }

    private BasicFileAttributes regularFileAttributes(Path path) throws IOException {
        requireSafeDirectory(path.getParent());
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isRegularFile() || attributes.isSymbolicLink()) {
            throw new IOException("private Files content file is unavailable or unsafe");
        }
        requireOwnerOnlyPermissions(path, false);
        return attributes;
    }

    private BasicFileAttributes directoryAttributes(Path path) throws IOException {
        requireSafeDirectory(path);
        BasicFileAttributes attributes = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
            throw new IOException("private Files content directory is unavailable or unsafe");
        }
        return attributes;
    }

    private void requireOwnerOnlyPermissions(Path path, boolean directory) throws IOException {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(
                    path, LinkOption.NOFOLLOW_LINKS);
            Set<PosixFilePermission> expected = directory
                    ? PosixFilePermissions.fromString("rwx------")
                    : PosixFilePermissions.fromString("rw-------");
            if (!permissions.equals(expected)) {
                throw new IOException("private Files content permissions are unsafe");
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX deployments rely on the filesystem ACL established during creation.
        }
    }

    private void ensurePrivateRegularFile(Path path) throws IOException {
        regularFileAttributes(path);
    }

    private void setPrivateDirectoryPermissions(Path directory) throws IOException {
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"));
        } catch (UnsupportedOperationException ignored) {
            // Deployment filesystems with POSIX permissions are tightened; other platforms rely on ACLs.
        }
    }

    private void setPrivateFilePermissions(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
            // Deployment filesystems with POSIX permissions are tightened; other platforms rely on ACLs.
        }
    }

    private void forceDirectory(Path directory) throws IOException {
        requireSafeDirectory(directory);
        try (FileChannel channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private void writeFully(FileChannel output, byte[] bytes, int offset, int length)
            throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, length);
        while (buffer.hasRemaining()) {
            output.write(buffer);
        }
    }

    private static long multipliedCapacity(long maximum, int concurrency) {
        try {
            return Math.multiplyExact(maximum, (long) concurrency);
        } catch (ArithmeticException overflow) {
            throw new IllegalArgumentException("private content capacity overflow", overflow);
        }
    }

    private long addExactCapacity(long left, long right) throws IOException {
        try {
            return Math.addExact(left, right);
        } catch (ArithmeticException overflow) {
            throw new IOException("private content reservation accounting overflow", overflow);
        }
    }

    private boolean exceeds(long used, long requested, long capacity) {
        return requested < 0 || used > capacity || requested > capacity - used;
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required", impossible);
        }
    }

    private ApiErrorException invalidIngress() {
        return error(
                HttpStatus.BAD_REQUEST,
                "file-upload-invalid-request",
                "The Files PUT request framing is invalid.",
                null);
    }

    private ApiErrorException sizeMismatch() {
        return error(
                HttpStatus.BAD_REQUEST,
                "file-upload-size-mismatch",
                "The Files PUT body does not match its declared content length.",
                null);
    }

    private ApiErrorException unreadableIngress(Throwable cause) {
        return error(
                HttpStatus.BAD_REQUEST,
                "file-upload-unreadable",
                "The Files PUT body could not be read completely.",
                cause);
    }

    private ApiErrorException contentTooLarge() {
        return error(
                HttpStatus.CONTENT_TOO_LARGE,
                "files-content-too-large",
                "The Files representation exceeds the accepted content bound.",
                null);
    }

    private ApiErrorException storageUnavailable() {
        return storageUnavailable(null);
    }

    private ApiErrorException storageUnavailable(Throwable cause) {
        return error(
                HttpStatus.INSUFFICIENT_STORAGE,
                "files-content-storage-unavailable",
                "Private Files ingress storage is unavailable.",
                cause);
    }

    private ApiErrorException capacityUnavailable() {
        return capacityUnavailable(null);
    }

    private ApiErrorException capacityUnavailable(Throwable cause) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-streaming-capacity-unavailable",
                "Bounded Files content capacity is temporarily unavailable.",
                cause);
    }

    private ApiErrorException contentIntegrityUnavailable(Throwable cause) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "file-content-integrity-unavailable",
                "The Files representation could not be verified for exact delivery.",
                cause);
    }

    private ApiErrorException streamingNotSupported(Throwable cause) {
        return error(
                HttpStatus.SERVICE_UNAVAILABLE,
                "files-streaming-not-supported",
                "The native Files bounded-content store is not ready for this generation.",
                cause);
    }

    private ApiErrorException error(
            HttpStatus status, String code, String message, Throwable cause) {
        ApiErrorException error = new ApiErrorException(status, code, message, REDACTED_DETAILS);
        if (cause != null) {
            error.initCause(cause);
        }
        return error;
    }

    private final class IngressHandle implements Ingress {
        private LockedObject object;
        private boolean bound;
        private final AtomicBoolean closed = new AtomicBoolean();
        private ReplayableFileContent content;

        private IngressHandle(LockedObject object, boolean bound) {
            this.object = Objects.requireNonNull(object, "object must not be null");
            this.bound = bound;
            this.content = contentFor(this);
        }

        @Override
        public ReplayableFileContent content() {
            if (closed()) {
                throw new IllegalStateException("private ingress handle is closed");
            }
            return content;
        }

        @Override
        public <T> T bindThroughPlanCommit(String operationRef, Supplier<T> transaction) {
            if (closed()) {
                throw new IllegalStateException("private ingress handle is closed");
            }
            String requiredOperationRef = requireOperationRef(operationRef);
            Objects.requireNonNull(transaction, "transaction must not be null");
            requireCurrentAuthority();
            try {
                bind(this, requiredOperationRef);
                content = contentFor(this);
                return transaction.get();
            } catch (RuntimeException failure) {
                handleTransactionFailure(this);
                throw failure;
            } catch (IOException failure) {
                handleTransactionFailure(this);
                throw storageUnavailable(failure);
            }
        }

        @Override
        public boolean releaseIfTerminal() {
            if (closed() || !bound || object.state().operationRef() == null) {
                return false;
            }
            if (safeProbe(object.state().operationRef()) != Protection.UNPROTECTED) {
                return false;
            }
            deleteAndCloseQuietly(object);
            markClosed();
            return true;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            LockedObject current = object;
            if (!bound) {
                deleteAndCloseQuietly(current);
            } else {
                current.close();
            }
        }

        private LockedObject object() {
            return object;
        }

        private boolean bound() {
            return bound;
        }

        private boolean closed() {
            return closed.get();
        }

        private void replace(LockedObject replacement, boolean replacementBound) {
            object = replacement;
            bound = replacementBound;
        }

        private void markClosed() {
            closed.set(true);
        }
    }

    private final class EgressHandle implements Egress {
        private final LockedObject object;
        private final AtomicBoolean opened = new AtomicBoolean();
        private final AtomicBoolean closed = new AtomicBoolean();
        private EgressInputStream stream;

        private EgressHandle(LockedObject object) {
            this.object = object;
        }

        @Override
        public synchronized InputStream openStream() throws IOException {
            if (closed.get() || !opened.compareAndSet(false, true)) {
                throw new IOException("verified Files egress can be opened exactly once");
            }
            try {
                requireCurrentAuthority();
                State state = object.state();
                if (state.phase() != StatePhase.SEALED_EGRESS) {
                    throw new IOException("private egress is not sealed");
                }
                verifyPrivateContent(object.directory(), state);
                ReplayableFileContent content = new ReplayableFileContent(
                        state.sizeBytes(),
                        state.digest(),
                        state.mediaType(),
                        () -> openContentForRead(object.directory(), state));
                stream = new EgressInputStream(content.openStream(), this);
                return stream;
            } catch (RuntimeException failure) {
                close();
                if (failure instanceof ApiErrorException apiFailure) {
                    throw new IOException(apiFailure.getMessage(), apiFailure);
                }
                throw new IOException("verified Files egress became unavailable", failure);
            } catch (IOException failure) {
                close();
                throw failure;
            }
        }

        @Override
        public synchronized void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            if (stream != null) {
                stream.closeDelegateQuietly();
            }
            deleteAndCloseQuietly(object);
        }
    }

    private static final class EgressInputStream extends FilterInputStream {
        private final EgressHandle owner;
        private final AtomicBoolean closed = new AtomicBoolean();

        private EgressInputStream(InputStream delegate, EgressHandle owner) {
            super(delegate);
            this.owner = owner;
        }

        @Override
        public void close() throws IOException {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            IOException failure = null;
            try {
                super.close();
            } catch (IOException unavailable) {
                failure = unavailable;
            } finally {
                owner.close();
            }
            if (failure != null) {
                throw failure;
            }
        }

        private void closeDelegateQuietly() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                super.close();
            } catch (IOException ignored) {
                // The owner still removes the private egress object.
            }
        }
    }

    private static final class ExactBoundedOutputStream extends OutputStream {
        private final FileChannel output;
        private final long expectedSize;
        private final MessageDigest digest;
        private long observed;

        private ExactBoundedOutputStream(
                FileChannel output, long expectedSize, MessageDigest digest) {
            this.output = output;
            this.expectedSize = expectedSize;
            this.digest = digest;
        }

        @Override
        public void write(int value) throws IOException {
            byte[] one = {(byte) value};
            write(one, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            Objects.checkFromIndexSize(offset, length, bytes.length);
            if (length == 0) {
                return;
            }
            if (observed > expectedSize || length > expectedSize - observed) {
                throw new IOException("verified Files source exceeded its exact size");
            }
            int cursor = offset;
            int remaining = length;
            while (remaining > 0) {
                int chunk = Math.min(remaining, TRANSFER_BUFFER_BYTES);
                ByteBuffer buffer = ByteBuffer.wrap(bytes, cursor, chunk);
                while (buffer.hasRemaining()) {
                    try {
                        output.write(buffer);
                    } catch (IOException failure) {
                        throw new PrivateStorageIOException(failure);
                    }
                }
                digest.update(bytes, cursor, chunk);
                observed += chunk;
                cursor += chunk;
                remaining -= chunk;
            }
        }

        private void requireComplete() throws IOException {
            if (observed != expectedSize) {
                throw new IOException("verified Files source ended before its exact size");
            }
        }
    }

    private static final class PrivateStorageIOException extends IOException {
        private PrivateStorageIOException(IOException cause) {
            super("private Files storage write failed", cause);
        }
    }

    private static final class LockedObject implements AutoCloseable {
        private Path directory;
        private final FileChannel ownerChannel;
        private final FileLock ownerLock;
        private State state;
        private final AtomicBoolean closed = new AtomicBoolean();

        private LockedObject(
                Path directory, FileChannel ownerChannel, FileLock ownerLock, State state) {
            this.directory = directory;
            this.ownerChannel = ownerChannel;
            this.ownerLock = ownerLock;
            this.state = state;
        }

        private Path directory() {
            return directory;
        }

        private void directory(Path directory) {
            this.directory = directory;
        }

        private State state() {
            return state;
        }

        private void state(State state) {
            this.state = state;
        }

        @Override
        public void close() {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            try {
                ownerLock.close();
            } catch (IOException ignored) {
                // Closing the channel below also releases the process lock.
            }
            try {
                ownerChannel.close();
            } catch (IOException ignored) {
                // No public diagnostics may expose the private owner marker.
            }
        }
    }

    private enum ObjectKind {
        INGRESS,
        EGRESS
    }

    private enum StatePhase {
        ACTIVE_INGRESS,
        SEALED_INGRESS,
        BOUND_INGRESS,
        ACTIVE_EGRESS,
        SEALED_EGRESS
    }

    private record State(
            ObjectKind kind,
            StatePhase phase,
            long reservedBytes,
            long sizeBytes,
            String digest,
            String mediaType,
            String operationRef,
            Instant createdAt,
            String volumeRef,
            String generationRef,
            String authorityRowDigest) {

        private static State active(
                ObjectKind kind,
                StatePhase phase,
                long reservedBytes,
                Instant createdAt,
                Authority authority,
                String authorityRowDigest) {
            return new State(
                    kind,
                    phase,
                    reservedBytes,
                    -1,
                    null,
                    null,
                    null,
                    createdAt,
                    authority.volumeRef(),
                    authority.generationRef(),
                    authorityRowDigest);
        }

        private State sealedIngress(long sizeBytes, String digest, String mediaType) {
            return new State(
                    kind,
                    StatePhase.SEALED_INGRESS,
                    sizeBytes,
                    sizeBytes,
                    digest,
                    mediaType,
                    null,
                    createdAt,
                    volumeRef,
                    generationRef,
                    authorityRowDigest);
        }

        private State sealedEgress(long sizeBytes, String digest, String mediaType) {
            return new State(
                    kind,
                    StatePhase.SEALED_EGRESS,
                    reservedBytes,
                    sizeBytes,
                    digest,
                    mediaType,
                    null,
                    createdAt,
                    volumeRef,
                    generationRef,
                    authorityRowDigest);
        }

        private State bound(String operationRef) {
            return new State(
                    kind,
                    StatePhase.BOUND_INGRESS,
                    reservedBytes,
                    sizeBytes,
                    digest,
                    mediaType,
                    operationRef,
                    createdAt,
                    volumeRef,
                    generationRef,
                    authorityRowDigest);
        }

        private boolean sameRepresentation(State other) {
            return other != null
                    && sizeBytes == other.sizeBytes
                    && Objects.equals(digest, other.digest)
                    && Objects.equals(mediaType, other.mediaType);
        }

        private byte[] serialize() {
            String value = String.join(
                    "\n",
                    "format=" + STATE_FORMAT,
                    "kind=" + kind.name(),
                    "phase=" + phase.name(),
                    "reservedBytes=" + reservedBytes,
                    "sizeBytes=" + sizeBytes,
                    "digest=" + value(digest),
                    "mediaType=" + encoded(mediaType),
                    "operationRef=" + encoded(operationRef),
                    "createdAt=" + createdAt.toString(),
                    "volumeRef=" + volumeRef,
                    "generationRef=" + generationRef,
                    "authorityRowDigest=" + authorityRowDigest,
                    "");
            return value.getBytes(StandardCharsets.UTF_8);
        }

        private static State parse(byte[] serialized) throws IOException {
            String text = new String(serialized, StandardCharsets.UTF_8);
            String[] lines = text.split("\n", -1);
            if (lines.length != 13 || !lines[12].isEmpty()) {
                throw new IOException("private content state shape is invalid");
            }
            Map<String, String> fields = new HashMap<>();
            for (int index = 0; index < 12; index++) {
                int separator = lines[index].indexOf('=');
                if (separator < 1
                        || fields.put(lines[index].substring(0, separator),
                                        lines[index].substring(separator + 1))
                                != null) {
                    throw new IOException("private content state fields are invalid");
                }
            }
            Set<String> exactKeys = Set.of(
                    "format",
                    "kind",
                    "phase",
                    "reservedBytes",
                    "sizeBytes",
                    "digest",
                    "mediaType",
                    "operationRef",
                    "createdAt",
                    "volumeRef",
                    "generationRef",
                    "authorityRowDigest");
            if (!fields.keySet().equals(exactKeys)
                    || !STATE_FORMAT.equals(fields.get("format"))) {
                throw new IOException("private content state contract is invalid");
            }
            try {
                State state = new State(
                        ObjectKind.valueOf(fields.get("kind")),
                        StatePhase.valueOf(fields.get("phase")),
                        Long.parseLong(fields.get("reservedBytes")),
                        Long.parseLong(fields.get("sizeBytes")),
                        nullable(fields.get("digest")),
                        decoded(fields.get("mediaType")),
                        decoded(fields.get("operationRef")),
                        Instant.parse(fields.get("createdAt")),
                        fields.get("volumeRef"),
                        fields.get("generationRef"),
                        fields.get("authorityRowDigest"));
                state.validate();
                return state;
            } catch (IllegalArgumentException failure) {
                throw new IOException("private content state values are invalid", failure);
            }
        }

        private void validate() {
            if (reservedBytes < 0
                    || createdAt == null
                    || volumeRef == null
                    || generationRef == null
                    || authorityRowDigest == null
                    || !authorityRowDigest.matches("sha256:[a-f0-9]{64}")) {
                throw new IllegalArgumentException("private content state is invalid");
            }
            boolean active = phase == StatePhase.ACTIVE_INGRESS || phase == StatePhase.ACTIVE_EGRESS;
            if (active) {
                if (sizeBytes != -1 || digest != null || mediaType != null || operationRef != null) {
                    throw new IllegalArgumentException("active private content state is invalid");
                }
                return;
            }
            if (sizeBytes < 0
                    || digest == null
                    || !digest.matches("sha256:[a-f0-9]{64}")
                    || mediaType == null
                    || mediaType.isBlank()) {
                throw new IllegalArgumentException("sealed private content state is invalid");
            }
            if (phase == StatePhase.BOUND_INGRESS
                    ? operationRef == null || operationRef.isBlank()
                    : operationRef != null) {
                throw new IllegalArgumentException("private content binding state is invalid");
            }
            if (kind == ObjectKind.INGRESS
                    && phase != StatePhase.SEALED_INGRESS
                    && phase != StatePhase.BOUND_INGRESS) {
                throw new IllegalArgumentException("private ingress phase is invalid");
            }
            if (kind == ObjectKind.EGRESS && phase != StatePhase.SEALED_EGRESS) {
                throw new IllegalArgumentException("private egress phase is invalid");
            }
        }

        private static String value(String value) {
            return value == null ? NONE : value;
        }

        private static String nullable(String value) {
            return NONE.equals(value) ? null : value;
        }

        private static String encoded(String value) {
            return value == null
                    ? NONE
                    : Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(value.getBytes(StandardCharsets.UTF_8));
        }

        private static String decoded(String value) {
            if (NONE.equals(value)) {
                return null;
            }
            return new String(
                    Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
        }
    }

    private record Received(long sizeBytes, String digest, String mediaType) {
    }

    private record CapacityUsage(
            long ingressBytes, long egressBytes, int activeIngress, int activeEgress) {
    }

    private static final class MutableScavengeReport {
        private int examined;
        private int deleted;
        private int protectedObjects;
        private int unavailableChecks;
        private int young;
        private int lockedOrChanged;

        private ScavengeReport freeze() {
            return new ScavengeReport(
                    examined,
                    deleted,
                    protectedObjects,
                    unavailableChecks,
                    young,
                    lockedOrChanged);
        }
    }

    /**
     * Keeps exactly one bounded directory traversal open across scheduled passes. This avoids
     * rediscovering an undeletable prefix while retaining only one iterator and one candidate in
     * memory. Namespace order is fixed; a completed cycle is reopened only by a later pass.
     */
    private final class ScavengeCandidateCursor implements AutoCloseable {
        private final List<Path> roots;
        private int rootIndex;
        private DirectoryStream<Path> stream;
        private Iterator<Path> iterator;

        private ScavengeCandidateCursor(List<Path> roots) {
            this.roots = List.copyOf(roots);
        }

        private Path next() throws IOException {
            while (rootIndex < roots.size()) {
                if (stream == null) {
                    Path root = roots.get(rootIndex);
                    requireSafeDirectory(root);
                    stream = Files.newDirectoryStream(root);
                    iterator = stream.iterator();
                }
                try {
                    if (iterator.hasNext()) {
                        Path candidate = contained(iterator.next());
                        if (!isSafeDirectory(candidate)) {
                            throw new IOException(
                                    "private content namespace contains an unsafe entry");
                        }
                        return candidate;
                    }
                } catch (DirectoryIteratorException failure) {
                    throw failure.getCause();
                }
                closeCurrent();
                rootIndex++;
            }
            return null;
        }

        private void closeCurrent() throws IOException {
            if (stream != null) {
                try {
                    stream.close();
                } finally {
                    stream = null;
                    iterator = null;
                }
            }
        }

        @Override
        public void close() throws IOException {
            closeCurrent();
        }
    }

    @FunctionalInterface
    private interface IoSupplier<T> {
        T get() throws Exception;
    }

    private static final class CapacityLockUnavailable extends IOException {
    }
}
