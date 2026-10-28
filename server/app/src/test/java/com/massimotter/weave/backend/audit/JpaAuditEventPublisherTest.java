package com.massimotter.weave.backend.audit;

import com.massimotter.weave.backend.audit.persistence.JpaAuditEventPublisher;

import com.massimotter.weave.backend.audit.persistence.AuditEventJpaRepository;
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

        assertThat(publisher(dataSource).events(AuditEventQuery.recent("tenant-a"))).containsExactly(event);
        assertThatThrownBy(() -> publisher(dataSource).publish(event(Map.of("result", "different"))))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessageContaining("Conflicting");
        assertThat(publisher.persistencePosture()).isEqualTo("portable-jpa-hibernate-validated");
    }

    @Test
    void scopedReadsApplyOrganizationEnvelopeOrderingAndPageBoundsInTheDatabase() {
        var dataSource = migratedDataSource();
        var publisher = publisher(dataSource);
        var now = Instant.parse("2026-07-09T10:00:00Z");
        for (int i = 0; i < 6; i++) {
            publisher.publish(new AuditEvent("tenant-a", "context-a", "user:subject", "files:user-http",
                    AuditAction.FILES_OPERATION_INTENT_RECORDED, now.plusSeconds(i), "file-" + i,
                    AuditRedactionLevel.SUPPORT_SAFE, Map.of("result", "completed")));
        }
        publisher.publish(new AuditEvent("tenant-b", "context-a", "user:other", "files:user-http",
                AuditAction.FILES_OPERATION_INTENT_RECORDED, now.plusSeconds(20), "foreign-private",
                AuditRedactionLevel.SUPPORT_SAFE, Map.of("result", "completed")));
        publisher.publish(new AuditEvent("tenant-a", "context-a", "user:subject", "files:user-http",
                AuditAction.FILES_OPERATION_INTENT_RECORDED, now.plusSeconds(21), "internal-only",
                AuditRedactionLevel.INTERNAL_REDACTED, Map.of("result", "completed")));
        publisher.publish(new AuditEvent("tenant-a", "context-a", "user:subject", "policy",
                AuditAction.ADMIN_POLICY_UPDATED, now.plusSeconds(22), "other-action",
                AuditRedactionLevel.SUPPORT_SAFE, Map.of("result", "completed")));
        var statistics = com.massimotter.weave.backend.testing.JpaTestDatabase.statistics(dataSource);
        statistics.clear();
        var query = new AuditEventQuery("tenant-a", java.util.List.of(AuditAction.FILES_OPERATION_INTENT_RECORDED),
                java.util.List.of(AuditRedactionLevel.SUPPORT_SAFE), 0, 2);
        assertThat(publisher.events(query)).extracting(AuditEvent::idempotencyKey)
                .containsExactly("file-5", "file-4");
        assertThat(statistics.getEntityLoadCount()).isEqualTo(2);
        assertThat(publisher.events(new AuditEventQuery("tenant-a", query.actions(), query.redactionLevels(), 1, 2)))
                .extracting(AuditEvent::idempotencyKey).containsExactly("file-3", "file-2");
        assertThat(publisher.events(AuditEventQuery.recent("tenant-a")))
                .noneMatch(event -> event.tenantId().equals("tenant-b"));
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
                .entityFirstDataSource("audit");
    }
}
