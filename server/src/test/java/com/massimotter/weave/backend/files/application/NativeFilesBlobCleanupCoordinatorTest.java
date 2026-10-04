package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition.ALREADY_ABSENT;
import static com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition.DELETED;
import static com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition.STILL_PROTECTED;
import static com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition.STILL_REFERENCED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.CleanupWork;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.Disposition;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.RecordedDisposition;
import com.massimotter.weave.backend.files.application.FilesBlobCleanupDispositionRepository.ReferenceStatus;
import com.massimotter.weave.backend.files.port.BlobStorePort;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NativeFilesBlobCleanupCoordinatorTest {
    private static final Instant NOW = Instant.parse("2026-08-20T12:00:00Z");
    private static final BlobStorePort.BlobScope SCOPE =
            new BlobStorePort.BlobScope("org-1", "space-1");

    @Test
    void recordsTheClosedDispositionSetInPrecedenceOrderAndReleasesOnlyWhenComplete() {
        BlobStorePort.BlobReference referenced = binding("a/referenced");
        BlobStorePort.BlobReference protectedBinding = binding("b/protected");
        BlobStorePort.BlobReference deleted = binding("c/deleted");
        BlobStorePort.BlobReference absent = binding("d/absent");
        FakeRepository repository = new FakeRepository(List.of(
                absent, deleted, referenced, protectedBinding, referenced));
        repository.statuses.put(referenced, ReferenceStatus.STILL_REFERENCED);
        repository.statuses.put(protectedBinding, ReferenceStatus.STILL_PROTECTED);
        repository.statuses.put(deleted, ReferenceStatus.DELETE_ALLOWED);
        repository.statuses.put(absent, ReferenceStatus.DELETE_ALLOWED);
        FakeBlobStore blobs = new FakeBlobStore(Set.of(deleted));
        NativeFilesBlobCleanupCoordinator coordinator = coordinator(repository, blobs);

        NativeFilesBlobCleanupCoordinator.CleanupResult partial = coordinator.process("op-1", 2);

        assertThat(partial.complete()).isFalse();
        assertThat(partial.plannedCount()).isEqualTo(4);
        assertThat(partial.recordedCount()).isEqualTo(2);
        assertThat(partial.processedCount()).isEqualTo(2);
        assertThat(blobs.deleted).isEmpty();

        NativeFilesBlobCleanupCoordinator.CleanupResult complete = coordinator.process("op-1", 100);

        assertThat(complete.complete()).isTrue();
        assertThat(complete.recordedCount()).isEqualTo(4);
        assertThat(complete.stillReferencedCount()).isEqualTo(1);
        assertThat(complete.stillProtectedCount()).isEqualTo(1);
        assertThat(complete.deletedCount()).isEqualTo(1);
        assertThat(complete.alreadyAbsentCount()).isEqualTo(1);
        assertThat(repository.dispositions.values())
                .extracting(RecordedDisposition::disposition)
                .containsExactlyInAnyOrder(
                        STILL_REFERENCED, STILL_PROTECTED, DELETED, ALREADY_ABSENT);
        assertThat(blobs.deleted).containsExactly(deleted, absent);

        NativeFilesBlobCleanupCoordinator.CleanupResult retry = coordinator.process("op-1", 100);

        assertThat(retry.complete()).isTrue();
        assertThat(retry.processedCount()).isZero();
        assertThat(blobs.deleted).containsExactly(deleted, absent);
    }

    @Test
    void failsClosedOnBindingDigestCollisionBeforeAnyBlobEffect() {
        FakeRepository repository = new FakeRepository(List.of(binding("a/one"), binding("b/two")));
        FakeBlobStore blobs = new FakeBlobStore(Set.of(binding("a/one"), binding("b/two")));
        String collision = FilesDigests.sha256("collision");
        NativeFilesBlobCleanupCoordinator coordinator = new NativeFilesBlobCleanupCoordinator(
                repository,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                ignored -> collision);

        assertThatThrownBy(() -> coordinator.process("op-1", 100))
                .isInstanceOf(NativeFilesBlobCleanupException.class)
                .hasMessage("Files blob cleanup binding digest collision");
        assertThat(repository.rechecks).isZero();
        assertThat(blobs.deleted).isEmpty();
    }

    @Test
    void failsClosedOnUnexpectedPreviouslyRecordedEvidence() {
        FakeRepository repository = new FakeRepository(List.of(binding("a/planned")));
        BlobStorePort.BlobReference unexpected = binding("b/unexpected");
        repository.dispositions.put(
                FilesDigests.sha256(unexpected.value()),
                row(unexpected, STILL_REFERENCED));
        FakeBlobStore blobs = new FakeBlobStore(Set.of());

        assertThatThrownBy(() -> coordinator(repository, blobs).process("op-1", 100))
                .isInstanceOf(NativeFilesBlobCleanupException.class)
                .hasMessage("Files blob cleanup disposition evidence is inconsistent");
        assertThat(repository.rechecks).isZero();
        assertThat(blobs.deleted).isEmpty();
    }

    @Test
    void rejectsUnboundedWorkRequests() {
        NativeFilesBlobCleanupCoordinator coordinator = coordinator(
                new FakeRepository(List.of()),
                new FakeBlobStore(Set.of()));

        assertThatThrownBy(() -> coordinator.process("op-1", 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> coordinator.process("op-1", 101))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NativeFilesBlobCleanupCoordinator coordinator(
            FakeRepository repository,
            FakeBlobStore blobs) {
        return new NativeFilesBlobCleanupCoordinator(
                repository,
                blobs,
                Clock.fixed(NOW, ZoneOffset.UTC),
                FilesDigests::sha256);
    }

    private static BlobStorePort.BlobReference binding(String value) {
        return new BlobStorePort.BlobReference(value);
    }

    private static RecordedDisposition row(
            BlobStorePort.BlobReference binding,
            Disposition disposition) {
        return new RecordedDisposition(
                "op-1",
                FilesBlobCleanupDispositionRepository.VERSION,
                FilesDigests.sha256(binding.value()),
                binding,
                disposition,
                NOW);
    }

    private static final class FakeRepository
            implements FilesBlobCleanupDispositionRepository {
        private final List<BlobStorePort.BlobReference> planned;
        private final Map<BlobStorePort.BlobReference, ReferenceStatus> statuses =
                new LinkedHashMap<>();
        private final Map<String, RecordedDisposition> dispositions = new LinkedHashMap<>();
        private int rechecks;

        private FakeRepository(List<BlobStorePort.BlobReference> planned) {
            this.planned = List.copyOf(planned);
        }

        @Override
        public CleanupWork lockWork(String operationRef) {
            return new CleanupWork(operationRef, SCOPE, planned);
        }

        @Override
        public List<RecordedDisposition> recorded(String operationRef) {
            return List.copyOf(dispositions.values());
        }

        @Override
        public ReferenceStatus recheck(
                CleanupWork work,
                BlobStorePort.BlobReference binding) {
            rechecks++;
            return statuses.getOrDefault(binding, ReferenceStatus.DELETE_ALLOWED);
        }

        @Override
        public void record(
                CleanupWork work,
                BlobStorePort.BlobReference binding,
                String bindingDigest,
                Disposition disposition,
                Instant recordedAt) {
            RecordedDisposition candidate = new RecordedDisposition(
                    work.operationRef(), VERSION, bindingDigest, binding, disposition, recordedAt);
            RecordedDisposition existing = dispositions.putIfAbsent(bindingDigest, candidate);
            if (existing != null && !existing.equals(candidate)) {
                throw new NativeFilesBlobCleanupException(
                        "Files cleanup disposition retry is contradictory");
            }
        }
    }

    private static final class FakeBlobStore implements BlobStorePort {
        private final Set<BlobReference> existing;
        private final List<BlobReference> deleted = new ArrayList<>();

        private FakeBlobStore(Set<BlobReference> existing) {
            this.existing = new LinkedHashSet<>(existing);
        }

        @Override public boolean configured() {
            return true;
        }

        @Override
        public BlobReceipt putStream(
                BlobScope scope,
                BlobReference reference,
                InputStream source,
                long expectedSize,
                String expectedDigest) {
            throw new UnsupportedOperationException();
        }

        @Override public void readStream(BlobScope scope, BlobReference reference, OutputStream target) {
            throw new UnsupportedOperationException();
        }

        @Override public Optional<BlobReceipt> receipt(BlobScope scope, BlobReference reference) {
            return existing.contains(reference)
                    ? Optional.of(new BlobReceipt(reference, FilesDigests.sha256(new byte[0]), 0))
                    : Optional.empty();
        }

        @Override public void delete(BlobScope scope, BlobReference reference) {
            deleted.add(reference);
            existing.remove(reference);
        }

        @Override public List<BlobReference> inventory(BlobScope scope, int limit) {
            return existing.stream().limit(limit).toList();
        }
    }
}
