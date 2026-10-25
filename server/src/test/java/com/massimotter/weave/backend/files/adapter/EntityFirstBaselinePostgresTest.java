package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.files.domain.FilesDomain;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.NestedExceptionUtils;

/** The candidate baseline comes from real production mappings, not a second SQL fixture. */
@Tag("postgres")
class EntityFirstBaselinePostgresTest {
    @TempDir Path migrationDirectory;

    @Test
    void generatedBaselineSupportsOrmIsolationAndNonDestructiveReinitialization() throws Exception {
        assertThat(Boolean.getBoolean("weave.test.postgres"))
                .as("this contract requires the PostgreSQL lane, never H2").isTrue();
        String output = System.getProperty("weave.entity-schema.output");
        assertThat(output).as("the export task must complete before this test").isNotBlank();
        Path baseline = Path.of(output);
        assertThat(baseline).isRegularFile();
        Files.copy(baseline, migrationDirectory.resolve("V1__entity_baseline.sql"));
        var dataSource = JpaTestDatabase.dataSource("entity_generated_baseline");
        assertThat(dataSource.getUrl()).startsWith("jdbc:postgresql:");
        var executor = Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false)
                .validateOnMigrate(true).load();
        assertThat(executor.migrate().migrationsExecuted).isEqualTo(1);
        executor.validate();
        JpaTestDatabase.validateSchema(dataSource);

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
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:other", "", 20))
                .isEmpty();

        // Same organization/binding/path, different identity: PostgreSQL must reject the duplicate.
        var replacement = resource("org:alpha", "owner:alpha", "file:two", FilesUserResource.State.ACTIVE);
        assertThatThrownBy(() -> files.save(replacement)).satisfies(failure -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23505");
        });
        assertThat(files.find("org:alpha", "file:two")).isEmpty();
        // Tombstoning releases the active path without deleting the historical identity.
        var tombstone = resource("org:alpha", "owner:alpha", "file:one", FilesUserResource.State.TOMBSTONED);
        assertThat(files.save(tombstone)).isEqualTo(tombstone);
        assertThat(files.save(replacement)).isEqualTo(replacement);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:alpha", "", 20))
                .containsExactly(replacement);

        assertThat(executor.migrate().migrationsExecuted).isZero();
        JpaTestDatabase.validateSchema(dataSource);
        assertThat(files.find("org:alpha", "file:one")).contains(tombstone);
        assertThat(files.find("org:alpha", "file:two")).contains(replacement);
        assertThat(files.find("org:beta", "file:one")).contains(beta);
    }

    private FilesUserResource resource(String organization, String owner, String fileId,
            FilesUserResource.State state) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new FilesUserResource(organization, fileId, 1L, "space:one", "root",
                "/example.txt", FilesDomain.Kind.FILE, owner, state,
                timestamp, timestamp, FilesUserResource.Scope.PRIVATE);
    }
}
