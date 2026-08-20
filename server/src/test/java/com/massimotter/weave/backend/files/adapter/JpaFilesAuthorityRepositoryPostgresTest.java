package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.application.FilesLockService;
import com.massimotter.weave.backend.files.application.FilesLockService.FileLockedException;
import com.massimotter.weave.backend.files.domain.FilesAuthority.CanonicalFileRecord;
import com.massimotter.weave.backend.files.domain.FilesAuthority.Lifecycle;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileId;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileObject;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.domain.FilesDomain.FileVersion;
import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.ConcurrentMutationException;
import com.massimotter.weave.backend.files.port.StoredFileRecord;
import com.massimotter.weave.backend.files.port.StoredFileRecord.BlobBinding;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class JpaFilesAuthorityRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void movePreservesCanonicalIdentityAndLocksPersistOnlyTokenDigests() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        Instant now = Instant.parse("2026-07-22T01:00:00Z");
        FileId stableId = new FileId("file:stable-document");
        FilePath original = new FilePath("/Documents/plan.md");
        FilePath moved = new FilePath("/Documents/core-plan.md");
        String blobReference =
                "v1/file/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        repository.save(new StoredFileRecord(
                new CanonicalFileRecord(
                        "org:example", "space:home",
                        new FileObject(stableId, original, Kind.FILE, 12, "text/markdown", now, false),
                        new FileVersion("etag-1"),
                        "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        1, Lifecycle.ACTIVE, now),
                new BlobBinding(blobReference)));

        StoredFileRecord afterMove = repository.move(
                "org:example", "space:home", stableId, original, moved, now.plusSeconds(1));
        var locks = new FilesLockService(repository, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        var granted = locks.acquire(
                "org:example", "space:home", moved, "person:alice", Duration.ofMinutes(30));

        assertThat(afterMove.metadata().object().id()).isEqualTo(stableId);
        assertThat(afterMove.metadata().object().path()).isEqualTo(moved);
        assertThat(afterMove.blobBinding()).isEqualTo(new BlobBinding(blobReference));
        assertThat(jdbc.queryForObject(
                "select storage_reference from weave_files_objects "
                        + "where organization_ref = ? and space_ref = ? and file_id = ?",
                String.class,
                "org:example",
                "space:home",
                stableId.value()))
                .isEqualTo(blobReference);
        assertThat(jdbc.queryForObject(
                "select token_digest from weave_file_locks where canonical_path = ?",
                String.class,
                moved.value()))
                .startsWith("sha256:")
                .isNotEqualTo(granted.token());
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home", moved, "opaquelocktoken:wrong", "person:alice"))
                .isInstanceOf(FileLockedException.class);
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home",
                new FilePath("/Documents/core-plan.md/attachment.txt"),
                null,
                "person:alice"))
                .isInstanceOf(FileLockedException.class);

        var afterRestart = new FilesLockService(
                repository(dataSource),
                Clock.fixed(now.plusSeconds(3), ZoneOffset.UTC));
        assertThatThrownBy(() -> afterRestart.requireUnlocked(
                "org:example", "space:home", moved, null, "person:alice"))
                .isInstanceOf(FileLockedException.class);

        FilePath movedAgain = new FilePath("/Archive/core-plan.md");
        afterRestart.move(
                "org:example", "space:home", moved, movedAgain,
                granted.token(), "person:alice");
        assertThat(repository.activeLock(
                "org:example", "space:home", moved, now.plusSeconds(3))).isEmpty();
        assertThat(repository.activeLock(
                "org:example", "space:home", movedAgain, now.plusSeconds(3))).isPresent();
        afterRestart.requireUnlocked(
                "org:example", "space:home", movedAgain,
                granted.token(), "person:alice");
        afterRestart.release(
                "org:example", "space:home", movedAgain,
                granted.token(), "person:alice");
        assertThat(repository.activeLock(
                "org:example", "space:home", movedAgain, now.plusSeconds(3))).isEmpty();
    }

    @Test
    void activationTranslatesActivePathRaceIntoCanonicalPortFailure() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String organization = "org:activation:" + suffix;
        String space = "space:home";
        FilePath path = new FilePath("/Activation/" + suffix + ".txt");
        Instant now = Instant.parse("2026-08-19T02:00:00Z");
        StoredFileRecord first = activeFile(
                organization,
                space,
                new FileId("file:first:" + suffix),
                path,
                "a",
                now);
        StoredFileRecord competing = activeFile(
                organization,
                space,
                new FileId("file:second:" + suffix),
                path,
                "b",
                now.plusSeconds(1));

        repository.activate(first);

        assertThatThrownBy(() -> repository.activate(competing))
                .isInstanceOf(ConcurrentMutationException.class)
                .hasMessageContaining(path.value());
        assertThat(jdbc.queryForObject(
                """
                select count(*)
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and canonical_path = ?
                   and lifecycle_state = 'ACTIVE'
                """,
                Integer.class,
                organization,
                space,
                path.value()))
                .isEqualTo(1);
    }

    @Test
    void replaceTreePersistsPrivateBindingsAndRollsBackMetadataWithBindingOnConflict() {
        DriverManagerDataSource dataSource = dataSource();
        JpaTestDatabase.initializeSchema(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = repository(dataSource);
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String organization = "org:replacement:" + suffix;
        String space = "space:home";
        Instant now = Instant.parse("2026-08-19T04:00:00Z");

        StoredFileRecord source = activeFile(
                organization,
                space,
                new FileId("file:source:" + suffix),
                new FilePath("/Source/" + suffix + ".txt"),
                "c",
                now);
        StoredFileRecord copied = activeFile(
                organization,
                space,
                new FileId("file:copy:" + suffix),
                new FilePath("/Copy/" + suffix + ".txt"),
                "d",
                now.plusSeconds(1));
        repository.save(source);

        repository.replaceTree(copied.metadata().object().path(), List.of(), List.of(copied));

        assertThat(repository.findById(
                organization,
                space,
                copied.metadata().object().id()).orElseThrow().blobBinding())
                .isEqualTo(copied.blobBinding())
                .isNotEqualTo(source.blobBinding());
        assertThat(persistedBinding(jdbc, source)).isEqualTo(source.blobBinding().opaqueReference());
        assertThat(persistedBinding(jdbc, copied)).isEqualTo(copied.blobBinding().opaqueReference());

        StoredFileRecord copiedTombstone = tombstone(copied, now.plusSeconds(2));
        repository.replaceTree(
                copied.metadata().object().path(),
                List.of(copiedTombstone),
                List.of());

        assertThat(persistedLifecycle(jdbc, copied)).isEqualTo(Lifecycle.TOMBSTONED.name());
        assertThat(persistedBinding(jdbc, copied)).isEqualTo(copied.blobBinding().opaqueReference());

        StoredFileRecord original = activeFile(
                organization,
                space,
                new FileId("file:original:" + suffix),
                new FilePath("/Atomic/" + suffix + ".txt"),
                "e",
                now.plusSeconds(3));
        StoredFileRecord occupied = activeFile(
                organization,
                space,
                new FileId("file:occupied:" + suffix),
                new FilePath("/Occupied/" + suffix + ".txt"),
                "f",
                now.plusSeconds(4));
        StoredFileRecord colliding = activeFile(
                organization,
                space,
                new FileId("file:colliding:" + suffix),
                occupied.metadata().object().path(),
                "0",
                now.plusSeconds(5));
        repository.save(original);
        repository.save(occupied);

        assertThatThrownBy(() -> repository.replaceTree(
                original.metadata().object().path(),
                List.of(tombstone(original, now.plusSeconds(5))),
                List.of(colliding)))
                .isInstanceOf(ConcurrentMutationException.class)
                .hasMessageContaining(original.metadata().object().path().value());

        assertThat(persistedLifecycle(jdbc, original)).isEqualTo(Lifecycle.ACTIVE.name());
        assertThat(persistedBinding(jdbc, original)).isEqualTo(original.blobBinding().opaqueReference());
        assertThat(persistedLifecycle(jdbc, occupied)).isEqualTo(Lifecycle.ACTIVE.name());
        assertThat(persistedBinding(jdbc, occupied)).isEqualTo(occupied.blobBinding().opaqueReference());
        assertThat(jdbc.queryForObject(
                """
                select count(*)
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                Integer.class,
                organization,
                space,
                colliding.metadata().object().id().value()))
                .isZero();
    }

    private StoredFileRecord activeFile(
            String organization,
            String space,
            FileId id,
            FilePath path,
            String digestSeed,
            Instant observedAt) {
        String repeated = digestSeed.repeat(64);
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        organization,
                        space,
                        new FileObject(
                                id,
                                path,
                                Kind.FILE,
                                1,
                                "text/plain",
                                observedAt,
                                false),
                        new FileVersion("version-" + digestSeed),
                        "sha256:" + repeated,
                        1,
                        Lifecycle.ACTIVE,
                        observedAt),
                new BlobBinding("v1/activation/" + repeated));
    }

    private StoredFileRecord tombstone(StoredFileRecord current, Instant observedAt) {
        CanonicalFileRecord metadata = current.metadata();
        return new StoredFileRecord(
                new CanonicalFileRecord(
                        metadata.organizationRef(),
                        metadata.spaceRef(),
                        metadata.object(),
                        metadata.version(),
                        metadata.contentDigest(),
                        metadata.providerBindingRevision(),
                        Lifecycle.TOMBSTONED,
                        observedAt),
                current.blobBinding());
    }

    private String persistedBinding(JdbcTemplate jdbc, StoredFileRecord record) {
        return jdbc.queryForObject(
                """
                select storage_reference
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                String.class,
                record.metadata().organizationRef(),
                record.metadata().spaceRef(),
                record.metadata().object().id().value());
    }

    private String persistedLifecycle(JdbcTemplate jdbc, StoredFileRecord record) {
        return jdbc.queryForObject(
                """
                select lifecycle_state
                  from weave_files_objects
                 where organization_ref = ?
                   and space_ref = ?
                   and file_id = ?
                """,
                String.class,
                record.metadata().organizationRef(),
                record.metadata().spaceRef(),
                record.metadata().object().id().value());
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private JpaFilesAuthorityRepository repository(
            DriverManagerDataSource dataSource) {
        return JpaTestDatabase.transactional(
                dataSource,
                new JpaFilesAuthorityRepository(
                        JpaTestDatabase.repository(
                                dataSource,
                                FileObjectJpaRepository.class),
                        JpaTestDatabase.repository(
                                dataSource,
                                FileLockJpaRepository.class)));
    }
}
