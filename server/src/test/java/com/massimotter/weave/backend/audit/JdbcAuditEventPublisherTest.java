package com.massimotter.weave.backend.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class JdbcAuditEventPublisherTest {

    private static final String ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION =
            "ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION";

    @TempDir
    Path tempDir;

    @Test
    void relationalAuditEventsSurvivePublisherRestart() {
        assertThat(ENTERPRISE_TARGET_AUDIT_PERSISTENCE_FOUNDATION).isNotBlank();
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource));

        publisher.publish(event("audit-provider-selection-001"));

        var restartedPublisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource));

        assertThat(restartedPublisher.events())
                .hasSize(1)
                .first()
                .satisfies(event -> {
                    assertThat(event.tenantId()).isEqualTo("weave-dogfood");
                    assertThat(event.contextId()).isEqualTo("admin-control-plane");
                    assertThat(event.actorRef()).isEqualTo("user:admin-123");
                    assertThat(event.sourceRef()).isEqualTo("provider-selection");
                    assertThat(event.action()).isEqualTo(AuditAction.ADMIN_POLICY_UPDATED);
                    assertThat(event.occurredAt()).isEqualTo(Instant.parse("2026-05-31T08:00:00Z"));
                    assertThat(event.idempotencyKey()).isEqualTo("audit-provider-selection-001");
                    assertThat(event.redactionLevel()).isEqualTo(AuditRedactionLevel.SECRET_REDACTED);
                    assertThat(event.payload()).containsEntry("category", "chat");
                    assertThat(event.payload()).containsEntry("supportSafe", true);
                    assertThat(event.payload()).containsEntry("token", "[redacted]");
                });
        assertThat(publisher.persistencePosture()).isEqualTo("durable-relational-flyway");
    }

    @Test
    void relationalPathMatchesExistingFilePublisherContractForAuditEvents() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        var filePublisher = new FileAuditEventPublisher(objectMapper, tempDir.resolve("audit-events.jsonl"));
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var jdbcPublisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource), objectMapper);
        AuditEvent event = event("audit-provider-selection-002");

        filePublisher.publish(event);
        jdbcPublisher.publish(event);

        assertThat(jdbcPublisher.events()).containsExactlyElementsOf(filePublisher.events());
    }

    @Test
    void flywaySchemaBaselineAddsAuditEventsTableWithoutProductionReadinessClaim() {
        DriverManagerDataSource dataSource = dataSource();

        migrate(dataSource);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from information_schema.tables where table_name = 'WEAVE_AUDIT_EVENTS'",
                Integer.class))
                .isEqualTo(1);
        assertThat(new JdbcAuditEventPublisher(new JdbcTemplate(dataSource)).persistencePosture())
                .isEqualTo("durable-relational-flyway")
                .doesNotContain("postgresql-production-ready");
    }

    @Test
    void relationalAuditEventsAreRetrySafeByIdempotencyKey() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource));
        AuditEvent event = event("audit-provider-selection-003");

        publisher.publish(event);
        publisher.publish(event);

        assertThat(publisher.events()).containsExactly(event);
    }

    @Test
    void conflictingAuditEventsWithSameIdempotencyKeyFailClosed() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource));
        AuditEvent event = event("audit-provider-selection-004");
        AuditEvent conflicting = new AuditEvent(
                event.tenantId(),
                event.contextId(),
                event.actorRef(),
                "different-source",
                event.action(),
                event.occurredAt(),
                event.idempotencyKey(),
                event.redactionLevel(),
                event.payload());

        publisher.publish(event);

        assertThatThrownBy(() -> publisher.publish(conflicting))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessage("Conflicting durable audit event for idempotency key.")
                .hasMessageNotContaining(event.tenantId())
                .hasMessageNotContaining(event.idempotencyKey());
        assertThat(publisher.events()).containsExactly(event);
    }

    @Test
    void auditPayloadWriteFailuresUseGenericSupportSafePublicationMessage() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenThrow(new JsonProcessingException("secret-provider-payload") {
        });
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource), objectMapper);

        assertThatThrownBy(() -> publisher.publish(event("audit-provider-selection-payload-write-failure")))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessage("durable audit publication failed")
                .hasMessageNotContaining("serialize")
                .hasMessageNotContaining("secret-provider-payload");
    }

    @Test
    void auditPayloadReadFailuresUseGenericSupportSafeReadMessage() throws Exception {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        new JdbcAuditEventPublisher(new JdbcTemplate(dataSource)).publish(event("audit-provider-selection-payload-read-failure"));
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        when(objectMapper.readValue(anyString(), any(TypeReference.class)))
                .thenThrow(new JsonProcessingException("secret-provider-payload") {
                });
        var publisher = new JdbcAuditEventPublisher(new JdbcTemplate(dataSource), objectMapper);

        assertThatThrownBy(publisher::events)
                .isInstanceOf(AuditRequiredException.class)
                .hasMessage("durable audit read failed")
                .hasMessageNotContaining("load")
                .hasMessageNotContaining("secret-provider-payload");
    }

    @Test
    void jdbcPublishFailuresAreWrappedForSupportSafeCallers() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table weave_audit_events");
        var publisher = new JdbcAuditEventPublisher(jdbcTemplate);

        assertThatThrownBy(() -> publisher.publish(event("audit-provider-selection-write-failure")))
                .isInstanceOf(AuditRequiredException.class)
                .hasMessage("durable audit publication failed")
                .hasMessageNotContaining("WEAVE_AUDIT_EVENTS");
    }

    @Test
    void jdbcReadFailuresAreWrappedForSupportSafeCallers() {
        DriverManagerDataSource dataSource = dataSource();
        migrate(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("drop table weave_audit_events");
        var publisher = new JdbcAuditEventPublisher(jdbcTemplate);

        assertThatThrownBy(publisher::events)
                .isInstanceOf(AuditRequiredException.class)
                .hasMessage("durable audit read failed")
                .hasMessageNotContaining("WEAVE_AUDIT_EVENTS");
    }

    @Test
    void publisherRequiresJdbcTemplateWithDataSource() {
        assertThatThrownBy(() -> new JdbcAuditEventPublisher(new JdbcTemplate()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requires a JdbcTemplate with a DataSource");
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

    private DriverManagerDataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_UPPER=true;DB_CLOSE_DELAY=-1");
        dataSource.setUsername("sa");
        dataSource.setPassword("");
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
