package com.massimotter.weave.backend.files.adapter;

import static com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.DescendantDepth.INFINITE;
import static com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.DescendantDepth.ONE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesRootIdentity;
import com.massimotter.weave.backend.files.application.FilesScope;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.InvalidSyncStateException;
import com.massimotter.weave.backend.files.application.NativeFilesWebDavSyncRepository.SyncReadCapacityException;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesChangeStream.ChangeKind;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

@Tag("postgres")
class JpaNativeFilesWebDavSyncRepositoryPostgresTest {

    private static final Instant NOW = Instant.parse("2026-08-23T19:00:00Z");
    private static final String DIGEST = "sha256:" + "a".repeat(64);
    private static final String GENERATION = "11111111-1111-4111-8111-111111111111";

    @Test
    void capturesInitialAndDeltaStateWithStableRootIdentityAndCompleteRanges() {
        Fixture fixture = fixture("files_webdav_sync_capture");
        FilesScope scope = fixture.scope();
        fixture.authority().activate(collection(scope, "collection-team", "/Team", 1));
        fixture.authority().activate(file(scope, "file-a", "/Team/a-renamed.txt", 3));
        fixture.authority().activate(file(scope, "file-nested", "/Team/Nested/deep.txt", 4));

        fixture.insertChange(1, 1, 1, "create-team", ChangeKind.CREATED,
                "collection-team", null, null, "/Team", Kind.COLLECTION, Lifecycle.ACTIVE);
        fixture.insertChange(2, 2, 2, "create-a", ChangeKind.CREATED,
                "file-a", null, null, "/Team/a.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.insertChange(3, 3, 3, "move-a", ChangeKind.MOVED,
                "file-a", "file-a", "/Team/a.txt", "/Team/a-renamed.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.insertChange(4, 4, 4, "create-deep", ChangeKind.CREATED,
                "file-nested", null, null, "/Team/Nested/deep.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.advanceHead(4);

        var root = fixture.repository().captureInitial(scope, new FilePath("/"), ONE, 100);
        assertThat(root.state().collectionId()).isEqualTo(FilesRootIdentity.forScope(scope));
        assertThat(root.state().streamRef()).isEqualTo(GENERATION);
        assertThat(root.changes())
                .extracting(change -> change.targetPath().value())
                .containsExactly("/Team");

        var immediate = fixture.repository().captureInitial(scope, new FilePath("/Team"), ONE, 100);
        assertThat(immediate.state().collectionId()).isEqualTo(new FileId("collection-team"));
        assertThat(immediate.capturedHighWater()).isEqualTo(4);
        assertThat(immediate.sourceTruncated()).isFalse();
        assertThat(immediate.changes())
                .extracting(change -> change.targetPath().value())
                .containsExactly("/Team/a-renamed.txt");

        var infinite = fixture.repository().captureInitial(scope, new FilePath("/Team"), INFINITE, 100);
        assertThat(infinite.changes())
                .extracting(change -> change.targetPath().value())
                .containsExactly("/Team/a-renamed.txt", "/Team/Nested/deep.txt");

        var delta = fixture.repository().captureDelta(
                scope,
                new FilePath("/Team"),
                INFINITE,
                new FileId("collection-team"),
                GENERATION,
                1,
                100);
        assertThat(delta.changes())
                .extracting(change -> change.revision())
                .containsExactly(2L, 3L, 4L);
        assertThat(delta.capturedHighWater()).isEqualTo(4);
        assertThat(delta.sourceTruncated()).isFalse();
    }

    @Test
    void rejectsWrongGenerationCollectionFutureFloorAndInsideRangeTokensBeforePaging() {
        Fixture fixture = fixture("files_webdav_sync_invalid_tokens");
        FilesScope scope = fixture.scope();
        fixture.authority().activate(collection(scope, "collection-team", "/Team", 1));
        fixture.authority().activate(file(scope, "file-a", "/Team/a.txt", 2));
        fixture.authority().activate(file(scope, "file-b", "/Team/b.txt", 3));
        fixture.insertChange(1, 1, 1, "create-team", ChangeKind.CREATED,
                "collection-team", null, null, "/Team", Kind.COLLECTION, Lifecycle.ACTIVE);
        fixture.insertChange(2, 2, 3, "create-pair", ChangeKind.CREATED,
                "file-a", null, null, "/Team/a.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.insertChange(3, 2, 3, "create-pair", ChangeKind.CREATED,
                "file-b", null, null, "/Team/b.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.advanceHead(3);

        assertInvalid(fixture, new FileId("wrong"), GENERATION, 1);
        assertInvalid(fixture, new FileId("collection-team"), UUID.randomUUID().toString(), 1);
        assertInvalid(fixture, new FileId("collection-team"), GENERATION, 2);
        assertInvalid(fixture, new FileId("collection-team"), GENERATION, 4);

        fixture.jdbc().update("""
                update weave_files_stream_heads
                   set reset_required_floor = 3
                 where organization_ref = ? and space_ref = ?
                """, scope.organizationRef(), scope.spaceRef());
        assertInvalid(fixture, new FileId("collection-team"), GENERATION, 1);
    }

    @Test
    void capacityNeverSplitsOneCanonicalMutationRange() {
        Fixture fixture = fixture("files_webdav_sync_capacity");
        FilesScope scope = fixture.scope();
        fixture.authority().activate(collection(scope, "collection-team", "/Team", 1));
        fixture.authority().activate(file(scope, "file-a", "/Team/a.txt", 2));
        fixture.authority().activate(file(scope, "file-b", "/Team/b.txt", 3));
        fixture.insertChange(1, 1, 1, "create-team", ChangeKind.CREATED,
                "collection-team", null, null, "/Team", Kind.COLLECTION, Lifecycle.ACTIVE);
        fixture.insertChange(2, 2, 3, "create-pair", ChangeKind.CREATED,
                "file-a", null, null, "/Team/a.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.insertChange(3, 2, 3, "create-pair", ChangeKind.CREATED,
                "file-b", null, null, "/Team/b.txt", Kind.FILE, Lifecycle.ACTIVE);
        fixture.advanceHead(3);

        assertThatThrownBy(() -> fixture.repository().captureInitial(
                        scope, new FilePath("/Team"), ONE, 1))
                .isInstanceOf(SyncReadCapacityException.class);
        assertThatThrownBy(() -> fixture.repository().captureDelta(
                        scope,
                        new FilePath("/Team"),
                        ONE,
                        new FileId("collection-team"),
                        GENERATION,
                        1,
                        1))
                .isInstanceOf(SyncReadCapacityException.class);

        var full = fixture.repository().captureDelta(
                scope,
                new FilePath("/Team"),
                ONE,
                new FileId("collection-team"),
                GENERATION,
                1,
                2);
        assertThat(full.changes()).extracting(change -> change.revision()).containsExactly(2L, 3L);
    }

    private static void assertInvalid(
            Fixture fixture,
            FileId collectionId,
            String generation,
            long afterRevision) {
        assertThatThrownBy(() -> fixture.repository().captureDelta(
                        fixture.scope(),
                        new FilePath("/Team"),
                        ONE,
                        collectionId,
                        generation,
                        afterRevision,
                        100))
                .isInstanceOf(InvalidSyncStateException.class);
    }

    private static Fixture fixture(String name) {
        DriverManagerDataSource dataSource = JpaTestDatabase.entityFirstDataSource(name);
        var files = JpaTestDatabase.repository(dataSource, FileObjectJpaRepository.class);
        var heads = JpaTestDatabase.repository(dataSource, FilesStreamHeadJpaRepository.class);
        var changes = JpaTestDatabase.repository(dataSource, FilesChangeJpaRepository.class);
        var volumes = JpaTestDatabase.repository(dataSource, FilesVolumeAuthorityJpaRepository.class);
        var authority = JpaTestDatabase.transactional(
                dataSource,
                new JpaFilesAuthorityRepository(
                        files,
                        JpaTestDatabase.repository(dataSource, FileLockJpaRepository.class)));
        FilesScope scope = new FilesScope("org:" + name, "space:home");
        heads.saveAndFlush(FilesStreamHeadJpaEntity.provision(scope.organizationRef(), scope.spaceRef(), NOW));
        volumes.saveAndFlush(new FilesVolumeAuthorityJpaEntity(
                "native-files",
                "22222222-2222-4222-8222-222222222222",
                GENERATION,
                "INITIAL_PROVISION",
                "33333333-3333-4333-8333-333333333333",
                "sha256:" + "b".repeat(64),
                "c".repeat(64),
                "sha256:" + "d".repeat(64),
                NOW));
        JpaNativeFilesWebDavSyncRepository target = new JpaNativeFilesWebDavSyncRepository(
                files,
                heads,
                changes,
                volumes,
                JpaTestDatabase.entityManager(dataSource));
        return new Fixture(
                scope,
                authority,
                JpaTestDatabase.transactional(dataSource, target),
                new JdbcTemplate(dataSource));
    }

    private static StoredFileRecord collection(
            FilesScope scope,
            String id,
            String path,
            long revision) {
        Instant observed = NOW.plusSeconds(revision);
        return new StoredFileRecord(new CanonicalFileRecord(
                scope.organizationRef(),
                scope.spaceRef(),
                new FileObject(new FileId(id), new FilePath(path), Kind.COLLECTION, 0, null, observed, false),
                FileVersion.unknown(),
                null,
                1,
                Lifecycle.ACTIVE,
                observed), null);
    }

    private static StoredFileRecord file(
            FilesScope scope,
            String id,
            String path,
            long revision) {
        Instant observed = NOW.plusSeconds(revision);
        String version = "v" + revision;
        return new StoredFileRecord(new CanonicalFileRecord(
                scope.organizationRef(),
                scope.spaceRef(),
                new FileObject(new FileId(id), new FilePath(path), Kind.FILE, revision, "text/plain", observed, false),
                new FileVersion(version),
                DIGEST,
                1,
                Lifecycle.ACTIVE,
                observed), new BlobBinding("v1/test/" + id));
    }

    private record Fixture(
            FilesScope scope,
            JpaFilesAuthorityRepository authority,
            JpaNativeFilesWebDavSyncRepository repository,
            JdbcTemplate jdbc) {

        void advanceHead(long latestRevision) {
            jdbc.update("""
                    update weave_files_stream_heads
                       set latest_revision = ?, updated_at_utc = ?
                     where organization_ref = ? and space_ref = ?
                    """,
                    latestRevision,
                    NOW.plusSeconds(latestRevision).atOffset(ZoneOffset.UTC),
                    scope.organizationRef(),
                    scope.spaceRef());
        }

        @SuppressWarnings("checkstyle:ParameterNumber")
        void insertChange(
                long revision,
                long rangeStart,
                long rangeEnd,
                String operationRef,
                ChangeKind changeKind,
                String fileRef,
                String sourceFileRef,
                String sourcePath,
                String targetPath,
                Kind kind,
                Lifecycle lifecycle) {
            OffsetDateTime time = NOW.plusSeconds(revision).atOffset(ZoneOffset.UTC);
            boolean collection = kind == Kind.COLLECTION;
            jdbc.update("""
                    insert into weave_files_changes (
                        organization_ref, space_ref, revision, operation_ref, change_kind,
                        file_ref, source_file_ref, source_path, target_path, object_kind,
                        lifecycle_state, provider_binding_revision, resulting_size,
                        resulting_media_type, resulting_content_digest, resulting_file_version,
                        resulting_etag, resulting_modified_at_utc, resulting_hidden,
                        resulting_observed_at_utc, range_start, range_end, committed_at_utc)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?, false, ?, ?, ?, ?)
                    """,
                    scope.organizationRef(),
                    scope.spaceRef(),
                    revision,
                    operationRef,
                    changeKind.name(),
                    fileRef,
                    sourceFileRef,
                    sourcePath,
                    targetPath,
                    kind.name(),
                    lifecycle.name(),
                    collection ? 0 : revision,
                    collection ? null : "text/plain",
                    collection ? null : DIGEST,
                    collection ? null : "v" + revision,
                    collection ? null : "\"etag-" + revision + "\"",
                    time,
                    time,
                    rangeStart,
                    rangeEnd,
                    time);
        }
    }
}
