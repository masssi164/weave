package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.domain.FilesDomain;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.testing.GeneratedEntityBaseline;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.util.ReflectionTestUtils;

/** Test the generated schema through real ORM writes, including deliberately malformed entities. */
@Tag("postgres")
class EntityFirstBaselinePostgresTest {
    @TempDir Path migrationDirectory;

    @Test
    void generatedBaselineSupportsOrmIsolationAndNonDestructiveReinitialization() throws Exception {
        var database = GeneratedEntityBaseline.create("entity_generated_files", migrationDirectory);
        var dataSource = database.dataSource();
        var repository = JpaTestDatabase.repository(dataSource, FilesUserResourceJpaRepository.class);
        var files = JpaTestDatabase.transactional(dataSource, new JpaFilesUserResourceRepository(repository));
        var alpha = resource("org:alpha", "owner:alpha", "file:one", FilesUserResource.State.ACTIVE);
        var beta = resource("org:beta", "owner:beta", "file:one", FilesUserResource.State.ACTIVE);
        assertThat(files.save(alpha)).isEqualTo(alpha);
        assertThat(files.find("org:alpha", "file:one")).contains(alpha);
        assertThat(files.find("org:beta", "file:one")).isEmpty();
        assertThat(files.save(beta)).isEqualTo(beta);
        assertThat(files.find("org:alpha", "file:one")).contains(alpha);
        assertThat(files.find("org:beta", "file:one")).contains(beta);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:alpha", "", 20))
                .containsExactly(alpha);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:other", "", 20)).isEmpty();

        var replacement = resource("org:alpha", "owner:alpha", "file:two", FilesUserResource.State.ACTIVE);
        rejected(() -> files.save(replacement), "23505");
        assertThat(files.find("org:alpha", "file:two")).isEmpty();

        // Deliberately bypass Java validation to prove database constraints, not just record checks.
        var nullKey = entity("null-key");
        ReflectionTestUtils.setField(nullKey, "activePathKey", null);
        rejected(() -> repository.saveAndFlush(nullKey), "23514");
        var wrongKey = entity("wrong-key");
        ReflectionTestUtils.setField(wrongKey, "activePathKey", "/different.txt");
        rejected(() -> repository.saveAndFlush(wrongKey), "23514");
        var zeroBinding = entity("zero-binding");
        ReflectionTestUtils.setField(zeroBinding, "bindingRevision", 0L);
        rejected(() -> repository.saveAndFlush(zeroBinding), "23514");
        var retainedTombstoneKey = entity("tombstone-key");
        ReflectionTestUtils.setField(retainedTombstoneKey, "state", FilesUserResource.State.TOMBSTONED);
        rejected(() -> repository.saveAndFlush(retainedTombstoneKey), "23514");
        for (String rejectedId : new String[]{"null-key", "wrong-key", "zero-binding", "tombstone-key"}) {
            assertThat(repository.findById(new FilesUserResourceId("org:invalid", rejectedId))).isEmpty();
        }

        var tombstone = resource("org:alpha", "owner:alpha", "file:one", FilesUserResource.State.TOMBSTONED);
        assertThat(files.save(tombstone)).isEqualTo(tombstone);
        assertThat(files.save(replacement)).isEqualTo(replacement);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:alpha", "", 20))
                .containsExactly(replacement);
        assertThat(database.migrations().migrate().migrationsExecuted).isZero();
        JpaTestDatabase.validateSchema(dataSource);
        assertThat(files.find("org:alpha", "file:one")).contains(tombstone);
        assertThat(files.find("org:alpha", "file:two")).contains(replacement);
        assertThat(files.find("org:beta", "file:one")).contains(beta);
    }

    private FilesUserResourceJpaEntity entity(String id) {
        var entity = FilesUserResourceJpaEntity.create(new FilesUserResourceId("org:invalid", id));
        entity.observe(resource("org:invalid", "owner:invalid", id, FilesUserResource.State.ACTIVE));
        return entity;
    }

    private void rejected(Runnable write, String state) {
        assertThatThrownBy(write::run).satisfies(failure -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo(state);
        });
    }

    private FilesUserResource resource(String organization, String owner, String fileId,
            FilesUserResource.State state) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new FilesUserResource(organization, fileId, 1L, "space:one", "root",
                "/example.txt", FilesDomain.Kind.FILE, owner, state,
                timestamp, timestamp, FilesUserResource.Scope.PRIVATE);
    }
}
