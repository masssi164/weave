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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class JdbcFilesAuthorityRepositoryPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void movePreservesCanonicalIdentityAndLocksPersistOnlyTokenDigests() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        var repository = new JdbcFilesAuthorityRepository(jdbc, new DataSourceTransactionManager(dataSource));
        Instant now = Instant.parse("2026-07-22T01:00:00Z");
        FileId stableId = new FileId("file:stable-document");
        FilePath original = new FilePath("/Documents/plan.md");
        FilePath moved = new FilePath("/Documents/core-plan.md");
        repository.save(new CanonicalFileRecord(
                "org:example", "space:home",
                new FileObject(stableId, original, Kind.FILE, 12, "text/markdown", now, false),
                new FileVersion("etag-1"),
                "sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                1, Lifecycle.ACTIVE, now));

        CanonicalFileRecord afterMove = repository.move(
                "org:example", "space:home", stableId, original, moved, now.plusSeconds(1));
        var locks = new FilesLockService(repository, Clock.fixed(now.plusSeconds(2), ZoneOffset.UTC));
        var granted = locks.acquire(
                "org:example", "space:home", moved, "person:alice", Duration.ofMinutes(30));

        assertThat(afterMove.object().id()).isEqualTo(stableId);
        assertThat(afterMove.object().path()).isEqualTo(moved);
        assertThat(jdbc.queryForObject(
                "select token_digest from weave_file_locks where canonical_path = ?", String.class, moved.value()))
                .startsWith("sha256:")
                .isNotEqualTo(granted.token());
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home", moved, "opaquelocktoken:wrong", "person:alice"))
                .isInstanceOf(FileLockedException.class);
        assertThatThrownBy(() -> locks.requireUnlocked(
                "org:example", "space:home", new FilePath("/Documents/core-plan.md/attachment.txt"),
                null, "person:alice"))
                .isInstanceOf(FileLockedException.class);

        var afterRestart = new FilesLockService(
                new JdbcFilesAuthorityRepository(jdbc, new DataSourceTransactionManager(dataSource)),
                Clock.fixed(now.plusSeconds(3), ZoneOffset.UTC));
        assertThatThrownBy(() -> afterRestart.requireUnlocked(
                "org:example", "space:home", moved, null, "person:alice"))
                .isInstanceOf(FileLockedException.class);

        FilePath movedAgain = new FilePath("/Archive/core-plan.md");
        afterRestart.move(
                "org:example", "space:home", moved, movedAgain, granted.token(), "person:alice");
        assertThat(repository.activeLock("org:example", "space:home", moved, now.plusSeconds(3))).isEmpty();
        assertThat(repository.activeLock("org:example", "space:home", movedAgain, now.plusSeconds(3))).isPresent();
        afterRestart.requireUnlocked("org:example", "space:home", movedAgain, granted.token(), "person:alice");
        afterRestart.release("org:example", "space:home", movedAgain, granted.token(), "person:alice");
        assertThat(repository.activeLock("org:example", "space:home", movedAgain, now.plusSeconds(3))).isEmpty();
    }

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }
}
