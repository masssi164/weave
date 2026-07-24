package com.massimotter.weave.shared.persistence;

import java.util.List;
import java.util.Objects;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Fails readiness unless the database is at the exact successful schema
 * version embedded in the shared persistence artifact.
 */
public final class SharedSchemaReadinessHealthIndicator implements HealthIndicator {
    private final JdbcTemplate jdbc;

    public SharedSchemaReadinessHealthIndicator(JdbcTemplate jdbc) {
        this.jdbc = Objects.requireNonNull(jdbc, "jdbc");
    }

    @Override
    public Health health() {
        try {
            List<Migration> migrations = jdbc.query(
                    "select version, success from flyway_schema_history where version is not null",
                    (row, ignored) -> new Migration(row.getString("version"), row.getBoolean("success")));
            boolean failed = migrations.stream().anyMatch(migration -> !migration.success());
            String latest = migrations.stream()
                    .filter(Migration::success)
                    .map(Migration::version)
                    .max(SharedSchemaReadinessHealthIndicator::compareVersions)
                    .orElse(null);
            boolean exact = SharedPersistenceModel.VERSION.equals(latest)
                    && migrations.stream().noneMatch(migration ->
                    compareVersions(migration.version(), SharedPersistenceModel.VERSION) > 0);
            if (!failed && exact) {
                return Health.up()
                        .withDetail("schemaVersion", SharedPersistenceModel.VERSION)
                        .withDetail("migrationOwner", "weave-server")
                        .build();
            }
            return Health.down()
                    .withDetail("expectedSchemaVersion", SharedPersistenceModel.VERSION)
                    .withDetail("observedSchemaVersion", latest == null ? "absent" : latest)
                    .withDetail("failedMigration", failed)
                    .build();
        } catch (DataAccessException unavailable) {
            return Health.down()
                    .withDetail("expectedSchemaVersion", SharedPersistenceModel.VERSION)
                    .withDetail("observedSchemaVersion", "unavailable")
                    .build();
        }
    }

    private static int compareVersions(String left, String right) {
        try {
            return Integer.compare(Integer.parseInt(left), Integer.parseInt(right));
        } catch (NumberFormatException invalid) {
            return left.compareTo(right);
        }
    }

    private record Migration(String version, boolean success) {
    }
}
