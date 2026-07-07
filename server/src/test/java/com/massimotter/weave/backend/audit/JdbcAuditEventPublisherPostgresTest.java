package com.massimotter.weave.backend.audit;

import java.time.Instant;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class JdbcAuditEventPublisherPostgresTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Test
    void postgresCompatibleMigrationPublishesSupportSafeAuditEventsWhenAvailable() {
        DriverManagerDataSource dataSource = postgresDataSource();
        migrate(dataSource);
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource));

        publisher.publish(event("audit-provider-selection-postgres-001"));

        assertThat(publisher.events()).hasSize(1);
        String payload = new JdbcTemplate(dataSource).queryForObject(
                "select payload_json from weave_audit_events where idempotency_key = ?",
                String.class,
                "audit-provider-selection-postgres-001");
        assertThat(payload)
                .contains("[redacted]")
                .doesNotContain("Bearer", "secret-token");
    }

    private AuditEvent event(String idempotencyKey) {
        return new AuditEvent(
                "weave-dogfood",
                "admin-control-plane",
                "user:admin-123",
                "provider-selection",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.parse("2026-05-31T08:00:00Z"),
                idempotencyKey,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "category", "chat",
                        "supportSafe", true,
                        "token", "Bearer secret-token"));
    }

    private DriverManagerDataSource postgresDataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(POSTGRES.getDriverClassName());
        dataSource.setUrl(POSTGRES.getJdbcUrl());
        dataSource.setUsername(POSTGRES.getUsername());
        dataSource.setPassword(POSTGRES.getPassword());
        return dataSource;
    }

    private void migrate(DriverManagerDataSource dataSource) {
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
