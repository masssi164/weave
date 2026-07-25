package com.massimotter.weave.shared.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class SharedSchemaReadinessHealthIndicatorTest {

    @Test
    void becomesReadyOnlyAtTheExactSharedSuccessfulMigrationVersion() {
        JdbcTemplate jdbc = migratedDatabase();
        var readiness = new SharedSchemaReadinessHealthIndicator(jdbc);

        assertThat(readiness.health().getStatus().getCode()).isEqualTo("UP");
        assertThat(readiness.health().getDetails())
                .containsEntry("schemaVersion", SharedPersistenceModel.VERSION)
                .containsEntry("migrationOwner", "weave-server");

        jdbc.update("update flyway_schema_history set version = '020' where version = '019'");
        assertThat(readiness.health().getStatus().getCode()).isEqualTo("DOWN");
        assertThat(readiness.health().getDetails())
                .containsEntry("expectedSchemaVersion", SharedPersistenceModel.VERSION)
                .containsEntry("observedSchemaVersion", "020");
    }

    @Test
    void remainsNotReadyForAbsentOrFailedHistory() {
        DriverManagerDataSource empty = dataSource();
        var absent = new SharedSchemaReadinessHealthIndicator(new JdbcTemplate(empty));
        assertThat(absent.health().getStatus().getCode()).isEqualTo("DOWN");

        JdbcTemplate jdbc = migratedDatabase();
        jdbc.update("update flyway_schema_history set success = false where version = '019'");
        assertThat(new SharedSchemaReadinessHealthIndicator(jdbc).health().getStatus().getCode())
                .isEqualTo("DOWN");
    }

    private static JdbcTemplate migratedDatabase() {
        DriverManagerDataSource dataSource = dataSource();
        Flyway.configure()
                .dataSource(dataSource)
                .locations(SharedPersistenceModel.FLYWAY_LOCATION)
                .load()
                .migrate();
        return new JdbcTemplate(dataSource);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:schema-" + UUID.randomUUID()
                        + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }
}
