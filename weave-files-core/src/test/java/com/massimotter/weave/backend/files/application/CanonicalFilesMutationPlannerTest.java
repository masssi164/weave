package com.massimotter.weave.backend.files.application;

import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.ACTIVE;
import static com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle.TOMBSTONED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.massimotter.weave.backend.files.application.CanonicalFilesMutationPlanner.MutationScope;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileWrite;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository;
import com.massimotter.weave.backend.files.port.FilesMutationPlan;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Target;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CanonicalFilesMutationPlannerTest {

    private static final Instant NOW = Instant.parse("2026-08-20T08:05:00Z");
    private static final MutationScope SCOPE = new MutationScope(
            "operation:files-v7",
            "organization:alpha",
            "space:main",
            FilesDigests.sha256("canonical-arguments"),
            7);

    private InMemoryAuthority authority;
    private CanonicalFilesMutationPlanner planner;

    @BeforeEach
    void setUp() {
        authority = new InMemoryAuthority();
        planner = new CanonicalFilesMutationPlanner(
                authority,
                Clock.fixed(NOW, ZoneOffset.UTC));
        addCollection("collection:docs", "/docs");
        addCollection("collection:archive", "/archive");
        addFile("file:source", "/docs/source.txt", "source");
    }

    @Test
    void putPlansCompleteResultBeforeBlobAccess() {
        var plan = planner.put(
                SCOPE,
                new FileWrite(
                        new FilePath("/docs/source.txt"),
                        "replacement".getBytes(StandardCharsets.UTF_8),
                        "text/plain"));

        assertEquals(FilesMutationPlan.OperationKind.PUT, plan.operationKind());
        Target target = plan.targets().getFirst();
        assertEquals(ChangeKind.CONTENT_UPDATED, target.changeKind());
        assertEquals("file:source", target.sourceFileRef());
        assertEquals("file:source", target.targetFileRef());
        assertEquals("/docs/source.txt", target.sourcePath());
        assertEquals("/docs/source.txt", target.targetPath());
        assertTrue(target.sourceReadBlobBinding().startsWith("blob:"));
        assertTrue(target.resultBlobBinding().startsWith("v1/"));
        assertNotEquals(target.sourceContentDigest(), target.resultContentDigest());
        assertEquals(NOW, target.resultModifiedAt());
        assertTrue(target.resultStrongEtag().startsWith("\""));
    }

    @Test
    void copyPlansVictimTombstonesAndDistinctDestinationIdentitiesInByteOrder() {
        addCollection("collection:victim", "/archive/copied");
        addFile("file:victim-z", "/archive/copied/z.txt", "old-z");
        addFile("file:source-z", "/docs/z.txt", "z");
        addFile("file:source-umlaut", "/docs/ä.txt", "umlaut");

        var plan = planner.copy(
                SCOPE,
                new FilePath("/docs"),
                new FilePath("/archive/copied"),
                true);

        assertEquals(FilesMutationPlan.OperationKind.COPY, plan.operationKind());
        assertEquals(6, plan.targets().size());
        assertEquals(
                List.of(
                        "/archive/copied",
                        "/archive/copied",
                        "/archive/copied/source.txt",
                        "/archive/copied/z.txt",
                        "/archive/copied/z.txt",
                        "/archive/copied/ä.txt"),
                plan.targets().stream()
                        .map(target -> target.changeKind() == ChangeKind.COPIED
                                ? target.targetPath()
                                : target.sourcePath())
                        .toList());

        List<Target> copied = plan.targets().stream()
                .filter(target -> target.changeKind() == ChangeKind.COPIED)
                .toList();
        assertEquals(4, copied.size());
        assertTrue(copied.stream().allMatch(target -> !target.targetFileRef().equals(target.sourceFileRef())));
        assertTrue(copied.stream()
                .filter(target -> target.objectKind() == Kind.FILE)
                .allMatch(target -> !target.resultBlobBinding().equals(target.sourceReadBlobBinding())));
        assertNull(copied.stream()
                .filter(target -> target.objectKind() == Kind.COLLECTION)
                .findFirst()
                .orElseThrow()
                .resultBlobBinding());

        assertEquals(1, authority.activeFileReads());
        assertEquals(
                List.of(
                        FilesMutationPlan.FenceRole.REQUEST_TARGET,
                        FilesMutationPlan.FenceRole.SOURCE_MEMBER,
                        FilesMutationPlan.FenceRole.SOURCE_MEMBER,
                        FilesMutationPlan.FenceRole.SOURCE_MEMBER,
                        FilesMutationPlan.FenceRole.DESTINATION_TARGET,
                        FilesMutationPlan.FenceRole.DESTINATION_MEMBER),
                plan.fences().stream().map(FilesMutationPlan.Fence::fenceRole).toList());
        assertTrue(plan.fences().getFirst().expectedSubtreeDigest().startsWith("sha256:"));
        assertTrue(plan.fences().get(4).expectedSubtreeDigest().startsWith("sha256:"));
    }

    @Test
    void canonicalConditionsAreDeduplicatedOrderedAndUseStrongThenWeakComparison() {
        String current = FilesEtags.strong(
                authority.at("/docs/source.txt").metadata().object(),
                authority.at("/docs/source.txt").metadata().version());
        FilesMutationPlan.EntityTagCondition match = FilesMutationPlan.EntityTagCondition.parseHeader(
                "\"z\", " + current + ", \"z\"");
        assertEquals(
                "ETAG_SET:[\"\\\"" + current.substring(1, current.length() - 1)
                        + "\\\"\",\"\\\"z\\\"\"]",
                match.canonicalValue());
        planner.put(
                SCOPE,
                new FileWrite(new FilePath("/docs/source.txt"), new byte[] {1}, "text/plain"),
                match,
                FilesMutationPlan.EntityTagCondition.notSupplied());

        FilesMutationPlan.EntityTagCondition weak = FilesMutationPlan.EntityTagCondition.parseHeader(
                "W/" + current);
        assertThrows(FilesMutationPlanningException.class, () -> planner.put(
                SCOPE,
                new FileWrite(new FilePath("/docs/source.txt"), new byte[] {1}, "text/plain"),
                weak,
                FilesMutationPlan.EntityTagCondition.notSupplied()));
        assertThrows(FilesMutationPlanningException.class, () -> planner.put(
                SCOPE,
                new FileWrite(new FilePath("/docs/source.txt"), new byte[] {1}, "text/plain"),
                FilesMutationPlan.EntityTagCondition.notSupplied(),
                weak));
        assertThrows(IllegalArgumentException.class, () ->
                FilesMutationPlan.EntityTagCondition.parseHeader("*, \"other\""));
    }

    @Test
    void collectionEtagParticipatesInRequestConditions() {
        StoredFileRecord collection = authority.at("/docs");
        String etag = FilesEtags.strong(collection.metadata().object(), collection.metadata().version());
        var plan = planner.delete(
                SCOPE,
                new FilePath("/docs"),
                FileVersion.unknown(),
                FilesMutationPlan.EntityTagCondition.parseHeader(etag),
                FilesMutationPlan.EntityTagCondition.notSupplied());

        assertEquals(etag, plan.fences().getFirst().expectedStrongEtag());
        assertTrue(plan.fences().getFirst().expectedSubtreeDigest().startsWith("sha256:"));
    }

    @Test
    void moveAndDeletePreserveIdsAndFileBindingsInTheirPlannedResults() {
        StoredFileRecord source = authority.at("/docs/source.txt");
        var move = planner.move(
                SCOPE,
                new FilePath("/docs/source.txt"),
                new FilePath("/archive/source.txt"),
                false);

        Target moved = move.targets().getFirst();
        assertEquals(ChangeKind.MOVED, moved.changeKind());
        assertEquals(source.metadata().object().id().value(), moved.targetFileRef());
        assertEquals(source.blobBinding().opaqueReference(), moved.resultBlobBinding());

        var deletion = planner.delete(
                SCOPE,
                new FilePath("/docs"),
                authority.at("/docs").metadata().version());
        Target deletedFile = deletion.targets().stream()
                .filter(target -> target.objectKind() == Kind.FILE)
                .findFirst()
                .orElseThrow();
        assertEquals(ChangeKind.TOMBSTONED, deletedFile.changeKind());
        assertEquals(TOMBSTONED, deletedFile.resultLifecycleState());
        assertEquals(source.metadata().object().id().value(), deletedFile.targetFileRef());
        assertEquals(source.blobBinding().opaqueReference(), deletedFile.resultBlobBinding());
        assertNull(deletedFile.targetPath());
    }

    @Test
    void targetContractRejectsUnsafeIntegerAndTimestampValues() {
        Target valid = planner.put(
                SCOPE,
                new FileWrite(new FilePath("/docs/new.txt"), new byte[] {1}, "application/octet-stream"))
                .targets()
                .getFirst();

        assertThrows(IllegalArgumentException.class, () -> copy(valid,
                FilesMutationPlan.JSON_SAFE_INTEGER_MAX + 1,
                valid.resultObservedAt()));
        assertThrows(IllegalArgumentException.class, () -> copy(valid,
                valid.resultSize(),
                valid.resultObservedAt().plusNanos(1)));
    }

    private Target copy(Target source, long resultSize, Instant resultObservedAt) {
        return new Target(
                source.targetOrdinal(),
                source.changeKind(),
                source.sourceFileRef(),
                source.targetFileRef(),
                source.sourcePath(),
                source.targetPath(),
                source.objectKind(),
                source.resultLifecycleState(),
                source.sourceReadBlobBinding(),
                source.sourceSize(),
                source.sourceMediaType(),
                source.sourceContentDigest(),
                source.sourceFileVersion(),
                source.sourceStrongEtag(),
                source.sourceModifiedAt(),
                source.sourceHidden(),
                source.sourceObservedAt(),
                source.sourceLifecycleState(),
                source.resultBlobBinding(),
                resultSize,
                source.resultMediaType(),
                source.resultContentDigest(),
                source.resultFileVersion(),
                source.resultStrongEtag(),
                source.resultModifiedAt(),
                source.resultHidden(),
                resultObservedAt);
    }

    private void addCollection(String id, String path) {
        authority.save(record(
                id,
                path,
                Kind.COLLECTION,
                new byte[0],
                null,
                null));
    }

    private void addFile(String id, String path, String content) {
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        authority.save(record(
                id,
                path,
                Kind.FILE,
                bytes,
                "text/plain",
                new BlobBinding("blob:" + id.substring("file:".length()))));
    }

    private StoredFileRecord record(
            String id,
            String path,
            Kind kind,
            byte[] bytes,
            String mediaType,
            BlobBinding binding) {
        String digest = kind == Kind.FILE ? FilesDigests.sha256(bytes) : null;
        FileVersion version = new FileVersion(kind == Kind.FILE ? digest : "collection-version:" + id);
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        SCOPE.organizationRef(),
                        SCOPE.spaceRef(),
                        new FileObject(
                                new FileId(id),
                                new FilePath(path),
                                kind,
                                bytes.length,
                                mediaType,
                                NOW.minusSeconds(60),
                                false),
                        version,
                        digest,
                        SCOPE.providerBindingRevision(),
                        ACTIVE,
                        NOW.minusSeconds(60)),
                binding);
    }

    private static final class InMemoryAuthority implements FilesAuthorityRepository {
        private final Map<String, StoredFileRecord> records = new LinkedHashMap<>();
        private int activeFileReads;

        @Override
        public StoredFileRecord save(StoredFileRecord record) {
            records.put(record.metadata().object().id().value(), record);
            return record;
        }

        StoredFileRecord at(String path) {
            return findByPath(SCOPE.organizationRef(), SCOPE.spaceRef(), new FilePath(path)).orElseThrow();
        }

        @Override
        public Optional<StoredFileRecord> findByPath(String organizationRef, String spaceRef, FilePath path) {
            return records.values().stream()
                    .filter(record -> record.metadata().organizationRef().equals(organizationRef)
                            && record.metadata().spaceRef().equals(spaceRef)
                            && record.metadata().lifecycle() == ACTIVE
                            && record.metadata().object().path().equals(path))
                    .findFirst();
        }

        @Override
        public Optional<StoredFileRecord> findById(String organizationRef, String spaceRef, FileId id) {
            return Optional.ofNullable(records.get(id.value()));
        }

        @Override
        public List<StoredFileRecord> activeFiles(String organizationRef, String spaceRef) {
            activeFileReads++;
            return records.values().stream()
                    .filter(record -> record.metadata().organizationRef().equals(organizationRef)
                            && record.metadata().spaceRef().equals(spaceRef)
                            && record.metadata().lifecycle() == ACTIVE)
                    .toList();
        }

        int activeFileReads() {
            return activeFileReads;
        }

        @Override
        public List<StoredFileRecord> replace(
                List<StoredFileRecord> tombstones,
                List<StoredFileRecord> activations) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StoredFileRecord move(
                String organizationRef,
                String spaceRef,
                FileId id,
                FilePath expectedPath,
                FilePath destination,
                Instant movedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public FileLockRecord acquireLock(FileLockRecord requested, Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<FileLockRecord> activeLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                Instant now) {
            return Optional.empty();
        }

        @Override
        public List<FileLockRecord> activeLocks(String organizationRef, String spaceRef, Instant now) {
            return new ArrayList<>();
        }

        @Override
        public void releaseLock(
                String organizationRef,
                String spaceRef,
                FilePath path,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void moveLock(
                String organizationRef,
                String spaceRef,
                FilePath source,
                FilePath destination,
                String tokenDigest,
                String ownerRef,
                Instant now) {
            throw new UnsupportedOperationException();
        }
    }
}
