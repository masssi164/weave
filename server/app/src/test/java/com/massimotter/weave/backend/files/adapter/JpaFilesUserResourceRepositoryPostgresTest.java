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

@org.junit.jupiter.api.Tag("postgres")
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
        assertThat(first.scope()).isEqualTo(FilesUserResource.Scope.PRIVATE);
        FilesUserResource implicitShare = new FilesUserResource(first.organizationRef(), first.fileId(),
                first.bindingRevision(), first.spaceRef(), first.parentFileId(), first.path(), first.kind(),
                first.ownerPrincipalRef(), first.state(), first.createdAt(), first.modifiedAt(),
                FilesUserResource.Scope.SPACE);
        assertThatThrownBy(() -> repository.save(implicitShare)).isInstanceOf(IllegalArgumentException.class);
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

    @Test
    void sharedSpaceCandidatesIncludeOtherCreatorsWithoutLeakingPrivateOrForeignFiles() {
        var source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        JpaTestDatabase.validateSchema(source);
        var repository = JpaTestDatabase.transactional(source, new JpaFilesUserResourceRepository(
                JpaTestDatabase.repository(source, FilesUserResourceJpaRepository.class)));
        var privateAlice = scopedResource("file:01-private-a", "user:alice", "workspace-default",
                "org:visibility", FilesUserResource.Scope.PRIVATE, FilesUserResource.State.ACTIVE);
        var privateBob = scopedResource("file:02-private-b", "user:bob", "workspace-default",
                "org:visibility", FilesUserResource.Scope.PRIVATE, FilesUserResource.State.ACTIVE);
        var sharedAlice = scopedResource("file:03-shared-a", "user:alice", "workspace-default",
                "org:visibility", FilesUserResource.Scope.SPACE, FilesUserResource.State.ACTIVE);
        var sharedBob = scopedResource("file:04-shared-b", "user:bob", "workspace-default",
                "org:visibility", FilesUserResource.Scope.SPACE, FilesUserResource.State.ACTIVE);
        var otherSpace = scopedResource("file:05-other-space", "user:bob", "workspace-other",
                "org:visibility", FilesUserResource.Scope.SPACE, FilesUserResource.State.ACTIVE);
        var otherOrganization = scopedResource("file:06-other-org", "user:bob", "workspace-default",
                "org:other", FilesUserResource.Scope.SPACE, FilesUserResource.State.ACTIVE);
        var removed = scopedResource("file:07-removed", "user:bob", "workspace-default",
                "org:visibility", FilesUserResource.Scope.SPACE, FilesUserResource.State.TOMBSTONED);
        java.util.List.of(privateAlice, privateBob, sharedAlice, sharedBob, otherSpace,
                otherOrganization, removed).forEach(repository::save);

        assertThat(repository.activeInSpace("org:visibility", "workspace-default", "user:bob", "", 25))
                .containsExactly(privateBob, sharedAlice, sharedBob);
        assertThat(repository.activeInSpace("org:visibility", "workspace-default", "user:alice", "", 25))
                .containsExactly(privateAlice, sharedAlice, sharedBob);
        assertThat(repository.activeInSpace("org:visibility", "workspace-default", "user:alice", "", 2))
                .containsExactly(privateAlice, sharedAlice);
        assertThat(repository.activeInSpace("org:visibility", "workspace-default", "user:alice",
                sharedAlice.fileId(), 1)).containsExactly(sharedBob);
        assertThat(repository.activeInSpace("org:visibility", "workspace-default", "user:alice",
                sharedBob.fileId(), 25)).isEmpty();
        assertThat(repository.activeInSpace("org:visibility", "workspace-other", "user:alice", "", 25))
                .containsExactly(otherSpace);
        assertThat(repository.activeInSpace("org:other", "workspace-default", "user:alice", "", 25))
                .containsExactly(otherOrganization);
    }

    private FilesUserResource scopedResource(String fileId, String owner, String space,
            String organization, FilesUserResource.Scope scope, FilesUserResource.State state) {
        Instant now = Instant.parse("2026-10-21T00:00:00Z");
        return new FilesUserResource(organization, fileId, 1, space, "file:root",
                "/" + fileId.replace(':', '-'), Kind.FILE, owner, state, now, now, scope);
    }

    @Test
    void forwardVisibilityMigrationPreservesLegacyPrivateRowsAndRejectsUnknownScopes() {
        DriverManagerDataSource source = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        String schema = "files_visibility_upgrade";
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").target("12").load().migrate();
        var jdbc = new org.springframework.jdbc.core.JdbcTemplate(source);
        jdbc.update("INSERT INTO " + schema + ".weave_files_user_resources "
                + "(organization_ref,file_id,binding_revision,space_ref,parent_file_id,canonical_path,active_path_key,"
                + "object_kind,owner_principal_ref,lifecycle_state,created_at_utc,modified_at_utc,version) "
                + "VALUES (?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                "org:legacy", "file:legacy", 1L, "workspace-default", "file:root", "/legacy", "/legacy",
                "FILE", "user:alice", "ACTIVE");
        Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
                .locations("classpath:db/migration").load().migrate();
        assertThat(jdbc.queryForObject("SELECT visibility_scope FROM " + schema
                + ".weave_files_user_resources WHERE file_id = ?", String.class, "file:legacy"))
                .isEqualTo("PRIVATE");
        assertThatThrownBy(() -> jdbc.update("UPDATE " + schema
                + ".weave_files_user_resources SET visibility_scope = ? WHERE file_id = ?", "UNKNOWN", "file:legacy"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    private FilesUserResource resource(String fileId, FilesUserResource.State state, Instant modifiedAt) {
        return new FilesUserResource("org:files", fileId, 1, "workspace-default", "file:root",
                "/same.txt", Kind.FILE, "user:alice", state,
                Instant.parse("2026-10-04T12:00:00Z"), modifiedAt);
    }
}
