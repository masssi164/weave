package com.massimotter.weave.backend.audit;

import com.massimotter.weave.backend.persistence.jpa.audit.AuditEventJpaRepository;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION
class JpaAuditEventPublisherTest {

    @Test
    void appendOnlyEventSurvivesRestartAndIdempotentRetryRejectsDifferentPayload() {
        DriverManagerDataSource dataSource = migratedDataSource();
        JpaAuditEventPublisher publisher = publisher(dataSource);
        AuditEvent event = event(Map.of("result", "accepted"));

        publisher.publish(event);
        publisher(dataSource).publish(event);

        assertThat(publisher(dataSource).events()).containsExactly(event);
        assertThatThrownBy(() -> publisher(dataSource).publish(event(Map.of("result", "different"))))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessageContaining("Conflicting");
        assertThat(publisher.persistencePosture()).isEqualTo("portable-jpa-hibernate-validated");
    }

    private AuditEvent event(Map<String, Object> payload) {
        return new AuditEvent(
                "tenant-a",
                "context-a",
                "user:subject",
                "weave-server",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.parse("2026-07-09T10:00:00Z"),
                "audit-idempotency-key",
                AuditRedactionLevel.SUPPORT_SAFE,
                payload);
    }

    private JpaAuditEventPublisher publisher(DriverManagerDataSource dataSource) {
        AuditEventJpaRepository springData =
                com.massimotter.weave.backend.testing.JpaTestDatabase.repository(
                        dataSource, AuditEventJpaRepository.class);
        return new JpaAuditEventPublisher(
                springData, tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build());
    }

    private DriverManagerDataSource migratedDataSource() {
        return com.massimotter.weave.backend.testing.JpaTestDatabase
                .migratedDataSource("audit");
    }
}
