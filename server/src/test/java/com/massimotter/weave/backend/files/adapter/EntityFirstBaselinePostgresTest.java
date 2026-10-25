package com.massimotter.weave.backend.files.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.files.domain.FilesDomain;
import com.massimotter.weave.backend.files.domain.FilesUserResource;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
        // Copy exactly the generated candidate; do not discover old or unrelated migrations.
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
        var alpha = resource("org:alpha", "owner:alpha");
        var beta = resource("org:beta", "owner:beta");
        assertThat(files.save(alpha)).isEqualTo(alpha);
        assertThat(files.find("org:alpha", "file:one")).contains(alpha);
        assertThat(files.find("org:beta", "file:one")).isEmpty();
        // The same file identifier in a different organization must remain a distinct row.
        assertThat(files.save(beta)).isEqualTo(beta);
        assertThat(files.find("org:alpha", "file:one")).contains(alpha);
        assertThat(files.find("org:beta", "file:one")).contains(beta);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:alpha", "", 20))
                .containsExactly(alpha);
        assertThat(files.activeInSpace("org:alpha", "space:one", "owner:other", "", 20))
                .isEmpty();

        assertThat(executor.migrate().migrationsExecuted).isZero();
        JpaTestDatabase.validateSchema(dataSource);
        assertThat(files.find("org:alpha", "file:one")).contains(alpha);
        assertThat(files.find("org:beta", "file:one")).contains(beta);
    }

    private FilesUserResource resource(String organization, String owner) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new FilesUserResource(organization, "file:one", 1L, "space:one", "root",
                "/example.txt", FilesDomain.Kind.FILE, owner, FilesUserResource.State.ACTIVE,
                timestamp, timestamp, FilesUserResource.Scope.PRIVATE);
    }
}
