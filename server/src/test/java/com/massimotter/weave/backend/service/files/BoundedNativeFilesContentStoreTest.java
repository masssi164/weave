package com.massimotter.weave.backend.service.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort.ContentTargetUnavailableException;
import com.massimotter.weave.backend.files.port.FilesStreamingContentPort.Ingress;
import com.massimotter.weave.backend.files.port.VerifiedFileRead;
import com.massimotter.weave.backend.files.port.VerifiedFileRead.RepresentationHeaders;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority;
import com.massimotter.weave.backend.schema.NativeFilesVolumeAuthority.Authority;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BoundedNativeFilesContentStoreTest {

    private static final long MAXIMUM_BYTES = 1_024;
    private static final Instant NOW = Instant.parse("2026-08-20T10:00:00Z");

    @TempDir
    Path temporaryDirectory;

    private Path authorityRoot;
    private Authority authority;
    private AtomicReference<Authority> acceptedAuthority;
    private AtomicReference<BoundedNativeFilesContentStore.Protection> protection;

    @BeforeEach
    void createAuthority() throws Exception {
        authorityRoot = temporaryDirectory.resolve("native-files-root");
        Files.createDirectory(authorityRoot);
        authority = NativeFilesVolumeAuthority.mint(
                new NativeFilesVolumeAuthority.TransitionContext(
                        "INITIAL_PROVISION",
                        "weave-test",
                        "bounded-content-test",
                        "0".repeat(40)),
                "a".repeat(64));
        NativeFilesVolumeAuthority.createOrValidateMarker(authorityRoot, authority);
        acceptedAuthority = new AtomicReference<>(authority);
        protection = new AtomicReference<>(
                BoundedNativeFilesContentStore.Protection.UNPROTECTED);
    }

    @Test
    void acceptsFixedAndUnknownBodiesAtTheExactBoundUsingOnlySixtyFourKibibyteReads()
            throws Exception {
        byte[] content = bytes(Math.toIntExact(MAXIMUM_BYTES));
        AtomicInteger largestRead = new AtomicInteger();
        InputStream tracking = new ByteArrayInputStream(content) {
            @Override
            public synchronized int read(byte[] target, int offset, int length) {
                largestRead.accumulateAndGet(length, Math::max);
                return super.read(target, offset, length);
            }
        };

        try (Ingress fixed = store().receive(
                        MAXIMUM_BYTES, "application/octet-stream", () -> new ByteArrayInputStream(content));
                Ingress unknown = store().receive(null, "application/octet-stream", () -> tracking)) {
            assertThat(readAll(fixed)).isEqualTo(content);
            assertThat(readAll(unknown)).isEqualTo(content);
            assertThat(fixed.content().sizeBytes()).isEqualTo(MAXIMUM_BYTES);
            assertThat(unknown.content().sha256Digest()).matches("sha256:[a-f0-9]{64}");
            assertThat(largestRead).hasValueLessThanOrEqualTo(
                    BoundedNativeFilesContentStore.TRANSFER_BUFFER_BYTES);
        }
    }

    @Test
    void rejectsKnownOversizeBeforeOpeningAndUnknownOversizeAfterOnlyOneExtraByte() {
        AtomicBoolean opened = new AtomicBoolean();

        assertCode(
                () -> store().receive(
                        MAXIMUM_BYTES + 1,
                        "application/octet-stream",
                        () -> {
                            opened.set(true);
                            return new ByteArrayInputStream(new byte[0]);
                        }),
                413,
                "files-content-too-large");
        assertThat(opened).isFalse();

        AtomicInteger supplied = new AtomicInteger();
        InputStream source = new InputStream() {
            @Override
            public int read(byte[] target, int offset, int length) {
                int remaining = Math.toIntExact(MAXIMUM_BYTES + 100 - supplied.get());
                if (remaining <= 0) {
                    return -1;
                }
                int delivered = Math.min(length, remaining);
                supplied.addAndGet(delivered);
                return delivered;
            }

            @Override
            public int read() {
                return supplied.getAndIncrement() < MAXIMUM_BYTES + 100 ? 0 : -1;
            }
        };
        assertCode(
                () -> store().receive(null, "application/octet-stream", () -> source),
                413,
                "files-content-too-large");
        assertThat(supplied).hasValue(Math.toIntExact(MAXIMUM_BYTES + 1));
    }

    @Test
    void givesStableDiagnosticsForFixedSizeMismatchAndUnreadableSource() {
        assertCode(
                () -> store().receive(
                        5L, "text/plain", () -> new ByteArrayInputStream("four".getBytes(UTF_8))),
                400,
                "file-upload-size-mismatch");
        assertCode(
                () -> store().receive(
                        3L,
                        "text/plain",
                        () -> new InputStream() {
                            @Override
                            public int read() throws IOException {
                                throw new IOException("private source detail");
                            }
                        }),
                400,
                "file-upload-unreadable");
    }

    @Test
    void ownerLockIsContinuousThroughBindCallbackAndRecoveryReopensOnlyAfterClose()
            throws Exception {
        BoundedNativeFilesContentStore firstProcess = store();
        BoundedNativeFilesContentStore restartedProcess = store();
        Ingress received = firstProcess.receive(
                7L, "text/plain", () -> new ByteArrayInputStream("durable".getBytes(UTF_8)));

        String result = received.bindThroughPlanCommit("operation-1", () -> {
            assertCode(
                    () -> restartedProcess.reopen("operation-1"),
                    503,
                    "file-content-integrity-unavailable");
            return "committed";
        });
        assertThat(result).isEqualTo("committed");
        assertCode(
                () -> restartedProcess.reopen("operation-1"),
                503,
                "file-content-integrity-unavailable");
        received.close();

        try (Ingress reopened = restartedProcess.reopen("operation-1")) {
            assertThat(readAll(reopened)).isEqualTo("durable".getBytes(UTF_8));
        }
    }

    @Test
    void equivalentRebindReusesOneObjectAndDifferentRepresentationFailsClosed()
            throws Exception {
        BoundedNativeFilesContentStore store = store();
        byte[] content = "same".getBytes(UTF_8);
        try (Ingress first = store.receive(4L, "text/plain", () -> new ByteArrayInputStream(content))) {
            first.bindThroughPlanCommit("operation-duplicate", () -> true);
        }
        try (Ingress equivalent = store.receive(
                4L, "text/plain", () -> new ByteArrayInputStream(content))) {
            equivalent.bindThroughPlanCommit("operation-duplicate", () -> true);
            assertThat(readAll(equivalent)).isEqualTo(content);
        }
        assertThat(boundObjectCount()).isEqualTo(1);

        try (Ingress conflicting = store.receive(
                5L, "text/plain", () -> new ByteArrayInputStream("other".getBytes(UTF_8)))) {
            assertCode(
                    () -> conflicting.bindThroughPlanCommit(
                            "operation-duplicate", () -> true),
                    503,
                    "file-content-integrity-unavailable");
        }
        assertThat(boundObjectCount()).isEqualTo(1);
    }

    @Test
    void callbackFailureDeletesOnlyAfterRelationalProbeProvesNoCommit() {
        BoundedNativeFilesContentStore store = store();
        protection.set(BoundedNativeFilesContentStore.Protection.UNAVAILABLE);
        try (Ingress received = store.receive(
                4L, "text/plain", () -> new ByteArrayInputStream("keep".getBytes(UTF_8)))) {
            assertThatThrownBy(() -> received.bindThroughPlanCommit(
                            "operation-uncertain",
                            () -> {
                                throw new IllegalStateException("uncertain Tx1 return");
                            }))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(boundObjectCount()).isEqualTo(1);

        protection.set(BoundedNativeFilesContentStore.Protection.UNPROTECTED);
        assertThat(store.remove("operation-uncertain")).isTrue();
        assertThat(boundObjectCount()).isZero();
    }

    @Test
    void boundIngressReleasesOnlyAfterRelationalTerminalEvidence() {
        BoundedNativeFilesContentStore store = store();
        protection.set(BoundedNativeFilesContentStore.Protection.PROTECTED);
        try (Ingress received = store.receive(
                4L, "text/plain", () -> new ByteArrayInputStream("done".getBytes(UTF_8)))) {
            received.bindThroughPlanCommit("operation-terminal", () -> true);
            assertThat(received.releaseIfTerminal()).isFalse();
            assertThat(boundObjectCount()).isEqualTo(1);

            protection.set(BoundedNativeFilesContentStore.Protection.UNPROTECTED);
            assertThat(received.releaseIfTerminal()).isTrue();
            assertThat(boundObjectCount()).isZero();
            assertThat(received.releaseIfTerminal()).isFalse();
        }
    }

    @Test
    void exactEgressIsPreverifiedOneShotAndDeletedOnDisconnect() throws Exception {
        byte[] content = bytes(700);
        BoundedNativeFilesContentStore store = store();
        var egress = store.verify(read(content));

        InputStream stream = egress.openStream();
        assertThat(stream.readNBytes(9)).hasSize(9);
        stream.close();
        assertThat(privateContentObjectCount()).isZero();
        assertThatThrownBy(egress::openStream).isInstanceOf(IOException.class);

        assertCode(
                () -> store.verify(readWithDeclaredSize(content, content.length + 1L)),
                503,
                "file-content-integrity-unavailable");
        assertCode(
                () -> store.verify(readWithDeclaredSize(content, MAXIMUM_BYTES + 1)),
                503,
                "files-streaming-capacity-unavailable");
    }

    @Test
    void mapsCanonicalContentTargetUnavailabilityToStreamingCapacity() {
        VerifiedFileRead healthy = read("abc".getBytes(UTF_8));
        VerifiedFileRead targetUnavailable = new VerifiedFileRead(
                healthy.item(),
                healthy.version(),
                healthy.headers(),
                healthy.observedAt(),
                target -> {
                    throw new ContentTargetUnavailableException(
                            new IOException("private target detail"));
                });

        assertCode(
                () -> store().verify(targetUnavailable),
                503,
                "files-streaming-capacity-unavailable");
    }

    @Test
    void sealedEgressRetainsItsCrossInstancePermitUntilClientRelease() {
        BoundedNativeFilesContentStore first = store(2, 1);
        BoundedNativeFilesContentStore second = store(2, 1);
        var retained = first.verify(read("first".getBytes(UTF_8)));

        assertCode(
                () -> second.verify(read("second".getBytes(UTF_8))),
                503,
                "files-streaming-capacity-unavailable");
        retained.close();

        second.verify(read("second".getBytes(UTF_8))).close();
        assertThat(privateContentObjectCount()).isZero();
    }

    @Test
    void activeIngressPermitIsCrossInstanceAndReservedBeforeSourceOpen() throws Exception {
        BoundedNativeFilesContentStore first = store(1, 2);
        BoundedNativeFilesContentStore second = store(1, 2);
        CountDownLatch sourceOpened = new CountDownLatch(1);
        CountDownLatch finishSource = new CountDownLatch(1);
        AtomicReference<Ingress> completed = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread receiving = Thread.ofVirtual().start(() -> {
            try {
                completed.set(first.receive(1L, "application/octet-stream", () -> {
                    sourceOpened.countDown();
                    return new InputStream() {
                        private boolean delivered;

                        @Override
                        public int read() throws IOException {
                            try {
                                if (!finishSource.await(5, TimeUnit.SECONDS)) {
                                    throw new IOException("test source timeout");
                                }
                            } catch (InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                                throw new IOException(interrupted);
                            }
                            if (delivered) {
                                return -1;
                            }
                            delivered = true;
                            return 1;
                        }
                    };
                }));
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        assertThat(sourceOpened.await(5, TimeUnit.SECONDS)).isTrue();

        assertCode(
                () -> second.receive(
                        1L, "application/octet-stream", () -> new ByteArrayInputStream(new byte[] {2})),
                503,
                "files-streaming-capacity-unavailable");
        finishSource.countDown();
        receiving.join(TimeUnit.SECONDS.toMillis(5));
        assertThat(failure.get()).isNull();
        assertThat(completed.get()).isNotNull();
        completed.get().close();
    }

    @Test
    void retainedIngressByteReservationExhaustionIsInsufficientStorageNotSizeLimit() {
        BoundedNativeFilesContentStore store = new BoundedNativeFilesContentStore(
                authorityRoot,
                MAXIMUM_BYTES,
                2,
                2,
                MAXIMUM_BYTES,
                MAXIMUM_BYTES * 2,
                acceptedAuthority::get,
                ignored -> protection.get(),
                Clock.fixed(NOW, ZoneOffset.UTC));
        try (Ingress retained = store.receive(
                MAXIMUM_BYTES,
                "application/octet-stream",
                () -> new ByteArrayInputStream(bytes(Math.toIntExact(MAXIMUM_BYTES))))) {
            assertCode(
                    () -> store.receive(
                            1L,
                            "application/octet-stream",
                            () -> new ByteArrayInputStream(new byte[] {1})),
                    507,
                    "files-content-storage-unavailable");
        }
    }

    @Test
    void physicalPrivateIngressFailureIsInsufficientStorageNotUnreadableRequest() throws Exception {
        BoundedNativeFilesContentStore store = store();
        Path unbound = privateDirectory("unbound");
        if (!Files.getFileStore(unbound).supportsFileAttributeView("posix")) {
            return;
        }
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(unbound);
        try {
            Files.setPosixFilePermissions(unbound, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            assertCode(
                    () -> store.receive(
                            3L,
                            "text/plain",
                            () -> new ByteArrayInputStream("abc".getBytes(UTF_8))),
                    507,
                    "files-content-storage-unavailable");
        } finally {
            Files.setPosixFilePermissions(unbound, original);
        }
    }

    @Test
    void physicalPrivateEgressFailureIsCapacityUnavailableNotContentCorruption() throws Exception {
        BoundedNativeFilesContentStore store = store();
        Path egress = privateDirectory("egress");
        if (!Files.getFileStore(egress).supportsFileAttributeView("posix")) {
            return;
        }
        Set<PosixFilePermission> original = Files.getPosixFilePermissions(egress);
        try {
            Files.setPosixFilePermissions(egress, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_EXECUTE));
            assertCode(
                    () -> store.verify(read("abc".getBytes(UTF_8))),
                    503,
                    "files-streaming-capacity-unavailable");
        } finally {
            Files.setPosixFilePermissions(egress, original);
        }
    }

    @Test
    void scavengerHonorsAgeOwnerLockAndImmediateRelationalProtectionCheck() {
        BoundedNativeFilesContentStore store = store();
        Ingress locked = store.receive(
                4L, "text/plain", () -> new ByteArrayInputStream("keep".getBytes(UTF_8)));
        locked.bindThroughPlanCommit("operation-protected", () -> true);

        var lockedReport = store.scavenge(NOW.plusSeconds(1), 10);
        assertThat(lockedReport.deleted()).isZero();
        assertThat(lockedReport.lockedOrChanged()).isEqualTo(1);
        locked.close();

        protection.set(BoundedNativeFilesContentStore.Protection.PROTECTED);
        var protectedReport = store.scavenge(NOW.plusSeconds(1), 10);
        assertThat(protectedReport.protectedObjects()).isEqualTo(1);
        assertThat(protectedReport.deleted()).isZero();

        protection.set(BoundedNativeFilesContentStore.Protection.UNPROTECTED);
        var deletedReport = store.scavenge(NOW.plusSeconds(1), 10);
        assertThat(deletedReport.deleted()).isEqualTo(1);
        assertThat(boundObjectCount()).isZero();
    }

    @Test
    void scavengerDeletesAgeFencedCrashBeforeOwnerMarkerButNeverFromBoundNamespace()
            throws Exception {
        BoundedNativeFilesContentStore store = store();
        Path unbound = Files.walk(authorityRoot)
                .filter(path -> path.getFileName().toString().equals("unbound"))
                .findFirst()
                .orElseThrow();
        Path partial = unbound.resolve("crash-before-owner");
        Files.createDirectory(partial);
        Path bound = unbound.getParent().resolve("bound");
        Path corruptBound = bound.resolve("unknown-operation");
        Files.createDirectory(corruptBound);
        if (Files.getFileStore(partial).supportsFileAttributeView("posix")) {
            Files.setPosixFilePermissions(partial, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
            Files.setPosixFilePermissions(corruptBound, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE));
        }
        Files.setLastModifiedTime(partial, FileTime.from(NOW.minusSeconds(60)));
        Files.setLastModifiedTime(corruptBound, FileTime.from(NOW.minusSeconds(60)));

        var report = store.scavenge(NOW, 10);

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.unavailableChecks()).isEqualTo(1);
        assertThat(partial).doesNotExist();
        assertThat(corruptBound).exists();
    }

    @Test
    void scavengerBoundsDiscoveryAndAdvancesPastAProtectedPrefixAcrossScheduledPasses()
            throws Exception {
        BoundedNativeFilesContentStore store = store();
        int limit = 3;
        int protectedCandidates = 5;
        int deletableCandidates = 7;
        for (int index = 0; index < protectedCandidates; index++) {
            try (Ingress retained = store.receive(
                    1L,
                    "application/octet-stream",
                    () -> new ByteArrayInputStream(new byte[] {1}))) {
                retained.bindThroughPlanCommit(
                        "operation-protected-prefix-" + index,
                        () -> true);
            }
        }
        protection.set(BoundedNativeFilesContentStore.Protection.PROTECTED);
        Path egress = privateDirectory("egress");
        for (int index = 0; index < deletableCandidates; index++) {
            Path candidate = egress.resolve("zz-crash-" + index);
            Files.createDirectory(candidate);
            if (Files.getFileStore(candidate).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(candidate, Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
            }
            Files.setLastModifiedTime(candidate, FileTime.from(NOW.minusSeconds(60)));
        }

        int deleted = 0;
        int passes = 0;
        for (int pass = 0; pass < 8 && childDirectoryCount(egress) > 0; pass++) {
            var report = store.scavenge(NOW, limit);
            assertThat(report.examined()).isEqualTo(limit);
            deleted += report.deleted();
            passes++;
        }

        assertThat(passes).isEqualTo(4);
        assertThat(deleted).isEqualTo(deletableCandidates);
        assertThat(childDirectoryCount(egress)).isZero();
        assertThat(boundObjectCount()).isEqualTo(protectedCandidates);
    }

    @Test
    void rejectsGenerationChangeAndSymlinkSubstitutionWithoutDisclosingPaths() throws Exception {
        BoundedNativeFilesContentStore store = store();
        try (Ingress received = store.receive(
                4L, "text/plain", () -> new ByteArrayInputStream("safe".getBytes(UTF_8)))) {
            received.bindThroughPlanCommit("operation-symlink", () -> true);
        }
        Path content = Files.walk(authorityRoot)
                .filter(path -> path.getFileName().toString().equals("content.bin"))
                .findFirst()
                .orElseThrow();
        Path outside = temporaryDirectory.resolve("outside-content");
        Files.write(outside, "safe".getBytes(UTF_8));
        Files.delete(content);
        Files.createSymbolicLink(content, outside);

        assertCode(
                () -> store.reopen("operation-symlink"),
                503,
                "file-content-integrity-unavailable");
        assertThat(outside).hasContent("safe");

        Authority another = NativeFilesVolumeAuthority.mint(
                new NativeFilesVolumeAuthority.TransitionContext(
                        "INITIAL_PROVISION",
                        "weave-test",
                        "different-generation",
                        "0".repeat(40)),
                "a".repeat(64));
        acceptedAuthority.set(another);
        assertCode(
                () -> store.receive(
                        0L, "application/octet-stream", () -> new ByteArrayInputStream(new byte[0])),
                503,
                "files-streaming-not-supported");
    }

    @Test
    void createsPrivateOwnerContentAndStateWithOwnerOnlyPosixPermissions() throws Exception {
        try (Ingress received = store().receive(
                3L, "text/plain", () -> new ByteArrayInputStream("abc".getBytes(UTF_8)))) {
            Path object = Files.walk(authorityRoot)
                    .filter(path -> path.getFileName().toString().equals("content.bin"))
                    .map(Path::getParent)
                    .findFirst()
                    .orElseThrow();
            if (Files.getFileStore(object).supportsFileAttributeView("posix")) {
                assertThat(Files.getPosixFilePermissions(object)).isEqualTo(Set.of(
                        PosixFilePermission.OWNER_READ,
                        PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE));
                for (String file : Set.of("content.bin", "owner.lock", "state.meta")) {
                    assertThat(Files.getPosixFilePermissions(object.resolve(file))).isEqualTo(Set.of(
                            PosixFilePermission.OWNER_READ,
                            PosixFilePermission.OWNER_WRITE));
                }
            }
        }
    }

    private BoundedNativeFilesContentStore store() {
        return store(2, 2);
    }

    private BoundedNativeFilesContentStore store(int ingressConcurrency, int egressConcurrency) {
        return new BoundedNativeFilesContentStore(
                authorityRoot,
                MAXIMUM_BYTES,
                ingressConcurrency,
                egressConcurrency,
                MAXIMUM_BYTES * ingressConcurrency,
                MAXIMUM_BYTES * egressConcurrency,
                acceptedAuthority::get,
                ignored -> protection.get(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private Path privateDirectory(String name) throws IOException {
        try (var paths = Files.walk(authorityRoot)) {
            return paths.filter(Files::isDirectory)
                    .filter(path -> path.getFileName().toString().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    private long childDirectoryCount(Path root) throws IOException {
        try (var children = Files.list(root)) {
            return children.filter(Files::isDirectory).count();
        }
    }

    private VerifiedFileRead read(byte[] bytes) {
        return readWithDeclaredSize(bytes, bytes.length);
    }

    private VerifiedFileRead readWithDeclaredSize(byte[] bytes, long declaredSize) {
        FileObject item = new FileObject(
                new FileId("file-1"),
                new FilePath("/file.bin"),
                Kind.FILE,
                declaredSize,
                "application/octet-stream",
                NOW,
                false);
        return new VerifiedFileRead(
                item,
                new FileVersion("version-1"),
                new RepresentationHeaders(
                        declaredSize,
                        "application/octet-stream",
                        "\"version-1\"",
                        "no-transform"),
                NOW,
                target -> {
                    try {
                        target.write(bytes);
                    } catch (IOException failure) {
                        throw new UncheckedIOException(failure);
                    }
                });
    }

    private byte[] readAll(Ingress ingress) throws IOException {
        try (InputStream input = ingress.content().openStream();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            input.transferTo(output);
            if (input instanceof com.massimotter.weave.backend.files.port.ReplayableFileContent.ExactInputStream exact) {
                exact.requireComplete();
            }
            return output.toByteArray();
        }
    }

    private int boundObjectCount() {
        try {
            return Math.toIntExact(Files.walk(authorityRoot)
                    .filter(path -> path.getFileName().toString().equals("state.meta"))
                    .filter(path -> {
                        try {
                            return Files.readString(path).contains("phase=BOUND_INGRESS");
                        } catch (IOException unavailable) {
                            throw new UncheckedIOException(unavailable);
                        }
                    })
                    .count());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private int privateContentObjectCount() {
        try {
            return Math.toIntExact(Files.walk(authorityRoot)
                    .filter(path -> path.getFileName().toString().equals("content.bin"))
                    .count());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private void assertCode(ThrowingAction action, int status, String code) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(ApiErrorException.class, failure -> {
                    assertThat(failure.status().value()).isEqualTo(status);
                    assertThat(failure.code()).isEqualTo(code);
                    assertThat(failure.details()).containsEntry("diagnosticsRedacted", true);
                    assertThat(failure.details().toString())
                            .doesNotContain(authorityRoot.toString())
                            .doesNotContain(Long.toString(MAXIMUM_BYTES));
                });
    }

    private byte[] bytes(int size) {
        byte[] value = new byte[size];
        for (int index = 0; index < value.length; index++) {
            value[index] = (byte) (index % 251);
        }
        return value;
    }

    private static final java.nio.charset.Charset UTF_8 = StandardCharsets.UTF_8;

    @FunctionalInterface
    private interface ThrowingAction {
        void run() throws Exception;
    }
}
