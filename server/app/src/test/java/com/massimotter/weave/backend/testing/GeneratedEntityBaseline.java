package com.massimotter.weave.backend.testing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Shared fresh-baseline fixture; never accepts an external database or an alternate SQL model. */
public final class GeneratedEntityBaseline {
    private GeneratedEntityBaseline() {}

    public static Database create(String testName, Path migrationDirectory) throws IOException {
        if (!Boolean.getBoolean("weave.test.postgres")) {
            throw new IllegalStateException("Generated-baseline qualification requires the PostgreSQL test lane");
        }
        String output = System.getProperty("weave.entity-schema.output");
        if (output == null || output.isBlank() || !Files.isRegularFile(Path.of(output))) {
            throw new IllegalStateException("Run entitySchemaExport before the generated-baseline tests");
        }
        Files.createDirectories(migrationDirectory);
        Files.copy(Path.of(output), migrationDirectory.resolve("V1__entity_baseline.sql"));
        var dataSource = JpaTestDatabase.dataSource(testName);
        if (dataSource.getUrl() == null || !dataSource.getUrl().startsWith("jdbc:postgresql:")) {
            throw new IllegalStateException("The baseline fixture must use test-owned PostgreSQL");
        }
        var migrations = Flyway.configure().dataSource(dataSource)
                .locations("filesystem:" + migrationDirectory.toAbsolutePath())
                .baselineOnMigrate(false).cleanDisabled(true).outOfOrder(false)
                .validateMigrationNaming(true).validateOnMigrate(true).load();
        if (migrations.migrate().migrationsExecuted != 1) {
            throw new IllegalStateException("A fresh fixture must execute exactly one generated baseline");
        }
        migrations.validate();
        JpaTestDatabase.validateSchema(dataSource);
        return new Database(dataSource, migrations);
    }

    public record Database(DriverManagerDataSource dataSource, Flyway migrations) {}
}
