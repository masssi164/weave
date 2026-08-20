package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.application.CanonicalFilesBlobEffects.BlobEffectException;
import com.massimotter.weave.backend.files.application.CanonicalFilesMutationPlanner.MutationScope;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReceipt;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobReference;
import com.massimotter.weave.backend.files.port.BlobStorePort.BlobScope;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.files.port.ReplayableFileContent;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesBlobEffectsTest {

    private static final Instant NOW = Instant.parse("2026-08-20T09:00:00Z");
    private static final MutationScope SCOPE = new MutationScope(
            "operation:blob-effects",
            "organization:alpha",
            "space:main",
            FilesDigests.sha256("arguments"),
            3);
    private static final String PLAN_DIGEST = "sha256:" + "b".repeat(64);

    private InMemoryAuthority authority;
    private InMemoryBlobs blobs;
    private CanonicalFilesMutationPlanner planner;
    private CanonicalFilesBlobEffects effects;

    @BeforeEach
    void setUp() {
        authority = new InMemoryAuthority();
        blobs = new InMemoryBlobs();
        planner = new CanonicalFilesMutationPlanner(authority, Clock.fixed(NOW, ZoneOffset.UTC));
        effects = new CanonicalFilesBlobEffects(blobs);
        authority.save(collection("collection:docs", "/docs"));
        authority.save(collection("collection:archive", "/archive"));
    }

    @Test
    void putPublishesOnlyTheExactPlannedBytesAndIsIdempotent() {
        byte[] content = "planned".getBytes(StandardCharsets.UTF_8);
        AtomicInteger sourceOpens = new AtomicInteger();
        ReplayableFileContent replayable = content(content, sourceOpens);
        Sealed plan = planner.put(
                        SCOPE,
                        new FilePath("/docs/planned.txt"),
                        replayable)
                .seal(PLAN_DIGEST, PLAN_DIGEST, NOW);

        effects.execute(plan, replayable);
        effects.execute(plan, replayable);

        String binding = plan.targets().getFirst().resultBlobBinding();
        assertArrayEquals(content, blobs.values.get(new BlobReference(binding)));
        assertEquals(1, blobs.putAttempts);
        assertEquals(1, sourceOpens.get());
        assertThrows(BlobEffectException.class, () -> effects.execute(
                plan,
                content("different".getBytes(StandardCharsets.UTF_8))));
        assertEquals(1, blobs.putAttempts);
    }

    @Test
    void putRejectsShortOversizedAndDigestInvalidReplays() {
        byte[] planned = "planned".getBytes(StandardCharsets.UTF_8);
        ReplayableFileContent descriptor = content(planned);
        Sealed plan = planner.put(
                        SCOPE,
                        new FilePath("/docs/planned.txt"),
                        descriptor)
                .seal(PLAN_DIGEST, PLAN_DIGEST, NOW);

        assertThrows(BlobEffectException.class, () -> effects.execute(
                plan,
                replay(planned.length, FilesDigests.sha256(planned), "plan".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BlobEffectException.class, () -> effects.execute(
                plan,
                replay(planned.length, FilesDigests.sha256(planned), "planned-extra".getBytes(StandardCharsets.UTF_8))));
        assertThrows(BlobEffectException.class, () -> effects.execute(
                plan,
                replay(planned.length, FilesDigests.sha256(planned), "PlanNed".getBytes(StandardCharsets.UTF_8))));
        assertEquals(3, blobs.putAttempts);
        assertTrue(blobs.values.isEmpty());
    }

    @Test
    void putCapsEverySourceReadAtTheFixedTransferBuffer() {
        byte[] planned = new byte[ReplayableFileContent.TRANSFER_BUFFER_BYTES * 3 + 17];
        java.util.Arrays.fill(planned, (byte) 7);
        int[] largestRequestedRead = {0};
        ReplayableFileContent descriptor = new ReplayableFileContent(
                planned.length,
                FilesDigests.sha256(planned),
                "application/octet-stream",
                () -> new ByteArrayInputStream(planned) {
                    @Override
                    public synchronized int read(byte[] target, int offset, int length) {
                        largestRequestedRead[0] = Math.max(largestRequestedRead[0], length);
                        return super.read(target, offset, length);
                    }
                });
        Sealed plan = planner.put(
                        SCOPE,
                        new FilePath("/docs/large.bin"),
                        descriptor)
                .seal(PLAN_DIGEST, PLAN_DIGEST, NOW);

        effects.execute(plan, descriptor);

        assertTrue(largestRequestedRead[0] > 0);
        assertTrue(largestRequestedRead[0] <= ReplayableFileContent.TRANSFER_BUFFER_BYTES);
        assertArrayEquals(
                planned,
                blobs.values.get(new BlobReference(plan.targets().getFirst().resultBlobBinding())));
    }

    @Test
    void copyReadsAndPublishesEveryPlannedFileBinding() {
        byte[] source = "source".getBytes(StandardCharsets.UTF_8);
        String digest = FilesDigests.sha256(source);
        BlobReference sourceReference = new BlobReference("v1/source/file");
        blobs.values.put(sourceReference, source);
        authority.save(new StoredFileRecord(
                new CanonicalFileRecord(
                        SCOPE.organizationRef(),
                        SCOPE.spaceRef(),
                        new FileObject(
                                new FileId("file:source"),
                                new FilePath("/docs/source.txt"),
                                Kind.FILE,
                                source.length,
                                "text/plain",
                                NOW.minusSeconds(60),
                                false),
                        new FileVersion(digest),
                        digest,
                        SCOPE.providerBindingRevision(),
                        ACTIVE,
                        NOW.minusSeconds(60)),
                new BlobBinding(sourceReference.value())));
        Sealed plan = planner.copy(
                        SCOPE,
                        new FilePath("/docs/source.txt"),
                        new FilePath("/archive/source.txt"),
                        false)
                .seal(PLAN_DIGEST, PLAN_DIGEST, NOW);

        effects.execute(plan, null);
        effects.execute(plan, null);

        assertArrayEquals(
                source,
                blobs.values.get(new BlobReference(plan.targets().getFirst().resultBlobBinding())));
        assertEquals(1, blobs.readAttempts);
        assertEquals(1, blobs.putAttempts);
    }

    @Test
    void partialSubtreeCopyResumesOnlyMissingPlannedBlobEffects() {
        byte[] firstContent = "first".getBytes(StandardCharsets.UTF_8);
        byte[] secondContent = "second".getBytes(StandardCharsets.UTF_8);
        StoredFileRecord first = file(
                "file:first",
                "/docs/first.txt",
                "v1/source/first",
                firstContent);
        StoredFileRecord second = file(
                "file:second",
                "/docs/second.txt",
                "v1/source/second",
                secondContent);
        authority.save(first);
        authority.save(second);
        blobs.values.put(
                new BlobReference(first.blobBinding().opaqueReference()),
                firstContent);
        blobs.values.put(
                new BlobReference(second.blobBinding().opaqueReference()),
                secondContent);

        Sealed plan = planner.copy(
                        SCOPE,
                        new FilePath("/docs"),
                        new FilePath("/archive/docs-copy"),
                        false)
                .seal(PLAN_DIGEST, PLAN_DIGEST, NOW);
        var copiedFiles = plan.targets().stream()
                .filter(target -> target.changeKind()
                        == com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind.COPIED)
                .filter(target -> target.objectKind() == Kind.FILE)
                .toList();
        assertEquals(2, copiedFiles.size());

        var alreadyPublished = copiedFiles.getFirst();
        byte[] alreadyPublishedContent = alreadyPublished.sourceFileRef().equals("file:first")
                ? firstContent
                : secondContent;
        blobs.values.put(
                new BlobReference(alreadyPublished.resultBlobBinding()),
                alreadyPublishedContent);

        var receipts = effects.execute(plan, null);

        assertEquals(2, receipts.size());
        assertEquals(1, blobs.readAttempts);
        assertEquals(1, blobs.putAttempts);
        for (var target : copiedFiles) {
            byte[] expected = target.sourceFileRef().equals("file:first")
                    ? firstContent
                    : secondContent;
            assertArrayEquals(
                    expected,
                    blobs.values.get(new BlobReference(target.resultBlobBinding())));
        }
    }

    private StoredFileRecord collection(String id, String path) {
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        SCOPE.organizationRef(),
                        SCOPE.spaceRef(),
                        new FileObject(
                                new FileId(id),
                                new FilePath(path),
                                Kind.COLLECTION,
                                0,
                                null,
                                NOW.minusSeconds(60),
                                false),
                        new FileVersion("collection-version:" + id),
                        null,
                        SCOPE.providerBindingRevision(),
                        ACTIVE,
                        NOW.minusSeconds(60)),
                null);
    }

    private ReplayableFileContent content(byte[] value) {
        return content(value, new AtomicInteger());
    }

    private ReplayableFileContent content(byte[] value, AtomicInteger opens) {
        byte[] stable = value.clone();
        return new ReplayableFileContent(
                stable.length,
                FilesDigests.sha256(stable),
                "text/plain",
                () -> {
                    opens.incrementAndGet();
                    return new ByteArrayInputStream(stable);
                });
    }

    private ReplayableFileContent replay(long size, String digest, byte[] actual) {
        byte[] stable = actual.clone();
        return new ReplayableFileContent(
                size,
                digest,
                "text/plain",
                () -> new ByteArrayInputStream(stable));
    }

    private StoredFileRecord file(
            String id,
            String path,
            String binding,
            byte[] content) {
        String digest = FilesDigests.sha256(content);
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        SCOPE.organizationRef(),
                        SCOPE.spaceRef(),
                        new FileObject(
                                new FileId(id),
                                new FilePath(path),
                                Kind.FILE,
                                content.length,
                                "text/plain",
                                NOW.minusSeconds(60),
                                false),
                        new FileVersion(digest),
                        digest,
                        SCOPE.providerBindingRevision(),
                        ACTIVE,
                        NOW.minusSeconds(60)),
                new BlobBinding(binding));
    }

    private static final class InMemoryBlobs implements BlobStorePort {
        private final Map<BlobReference, byte[]> values = new LinkedHashMap<>();
        private int putAttempts;
        private int readAttempts;

        @Override public boolean configured() { return true; }

        @Override
        public BlobReceipt putStream(
                BlobScope scope,
                BlobReference reference,
                InputStream source,
                long expectedSize,
                String expectedDigest) {
            putAttempts++;
            try {
                byte[] content = source.readAllBytes();
                if (content.length != expectedSize || !FilesDigests.sha256(content).equals(expectedDigest)) {
                    throw new AssertionError("unexpected test blob");
                }
                byte[] existing = values.putIfAbsent(reference, content);
                if (existing != null && !java.util.Arrays.equals(existing, content)) {
                    throw new AssertionError("immutable blob changed");
                }
                return new BlobReceipt(reference, expectedDigest, expectedSize);
            } catch (java.io.IOException exception) {
                throw new IllegalStateException(exception);
            }
        }

        @Override
        public void readStream(BlobScope scope, BlobReference reference, OutputStream target) {
            readAttempts++;
            try {
                target.write(values.get(reference));
            } catch (java.io.IOException exception) {
                throw new AssertionError(exception);
            }
        }

        @Override
        public Optional<BlobReceipt> receipt(BlobScope scope, BlobReference reference) {
            byte[] value = values.get(reference);
            return value == null
                    ? Optional.empty()
                    : Optional.of(new BlobReceipt(reference, FilesDigests.sha256(value), value.length));
        }

        @Override public void delete(BlobScope scope, BlobReference reference) { values.remove(reference); }
        @Override public List<BlobReference> inventory(BlobScope scope, int limit) { return List.copyOf(values.keySet()); }
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final Map<String, StoredFileRecord> records = new LinkedHashMap<>();

        @Override public StoredFileRecord save(StoredFileRecord record) {
            records.put(record.metadata().object().id().value(), record);
            return record;
        }

        @Override
        public Optional<StoredFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) {
            return records.values().stream()
                    .filter(record -> record.metadata().lifecycle() == ACTIVE
                            && record.metadata().organizationRef().equals(organizationRef)
                            && record.metadata().spaceRef().equals(spaceRef)
                            && record.metadata().object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<StoredFileRecord> findById(String organizationRef, String spaceRef, FileId id) {
            return Optional.ofNullable(records.get(id.value()));
        }

        @Override
        public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) {
            return records.values().stream()
                    .filter(record -> record.metadata().lifecycle() == ACTIVE)
                    .toList();
        }

        @Override public List<StoredFileRecord> replace(List<StoredFileRecord> tombstones, List<StoredFileRecord> activations) { throw new UnsupportedOperationException(); }
        @Override public StoredFileRecord move(String organizationRef, String spaceRef, FileId id, FilePath expectedPath, FilePath destination, Instant movedAt) { throw new UnsupportedOperationException(); }
        @Override public FileLockRecord acquireLock(FileLockRecord requested, Instant now) { throw new UnsupportedOperationException(); }
        @Override public Optional<FileLockRecord> activeLock(String organizationRef, String spaceRef, FilePath path, Instant now) { return Optional.empty(); }
        @Override public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) { return List.of(); }
        @Override public void releaseLock(String organizationRef, String spaceRef, FilePath path, String tokenDigest, String ownerRef, Instant now) { throw new UnsupportedOperationException(); }
        @Override public void moveLock(String organizationRef, String spaceRef, FilePath source, FilePath destination, String tokenDigest, String ownerRef, Instant now) { throw new UnsupportedOperationException(); }
    }
}
