package com.massimotter.weave.backend.files.adapter;

import com.massimotter.weave.backend.files.domain.FilesDomain.Kind;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class JpaFilesUserResourceRepositoryPostgresTest {
    @Container private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void activePathCannotAcquireAnotherIdentityUntilFirstIsTombstoned() {
        DriverManagerDataSource source = new DriverManagerDataSource();
        source.setDriverClassName(POSTGRES.getDriverClassName());
        source.setUrl(POSTGRES.getJdbcUrl());
        source.setUsername(POSTGRES.getUsername());
        source.setPassword(POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);
        var repository = JpaTestDatabase.transactional(source, new JpaFilesUserResourceRepository(
                JpaTestDatabase.repository(source, FilesUserResourceJpaRepository.class)));
        Instant now = Instant.parse("2026-10-04T12:00:00Z");

        FilesUserResource first = resource("file:first", FilesUserResource.State.ACTIVE, now);
        FilesUserResource second = resource("file:second", FilesUserResource.State.ACTIVE, now);
        assertThat(repository.save(first)).isEqualTo(first);
        assertThatThrownBy(() -> repository.save(second)).isInstanceOf(RuntimeException.class);
        assertThat(repository.findActivePath("org:files", 1, "/same.txt"))
                .contains(first);

        FilesUserResource tombstone = resource("file:first", FilesUserResource.State.TOMBSTONED,
                now.plusSeconds(1));
        repository.save(tombstone);
        assertThat(repository.save(second)).isEqualTo(second);
        assertThat(repository.find("org:files", "file:first")).contains(tombstone);
        assertThat(repository.findActivePath("org:files", 1, "/same.txt")).contains(second);
        assertThat(repository.activeInSpace("org:files", "workspace-default",
                "user:alice", "", 25)).containsExactly(second);
        assertThat(repository.activeInSpace("org:files", "workspace-default",
                "user:bob", "", 25)).isEmpty();
        assertThat(repository.activeInSpace("org:files", "workspace-default",
                "user:alice", "file:second", 25)).isEmpty();
    }

    private FilesUserResource resource(String fileId, FilesUserResource.State state, Instant modifiedAt) {
        return new FilesUserResource("org:files", fileId, 1, "workspace-default", "file:root",
                "/same.txt", Kind.FILE, "user:alice", state,
                Instant.parse("2026-10-04T12:00:00Z"), modifiedAt);
    }
}
