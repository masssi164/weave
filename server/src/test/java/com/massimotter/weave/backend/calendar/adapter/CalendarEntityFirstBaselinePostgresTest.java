package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.testing.GeneratedEntityBaseline;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import jakarta.persistence.EntityManager;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

/** Calendar contracts execute only the ORM-generated baseline, never the historical SQL chain. */
@Tag("postgres")
class CalendarEntityFirstBaselinePostgresTest {
    @TempDir Path migrationDirectory;

    @Test
    void generatedSchemaEnforcesTemporalRecurrenceAndScopeIntegrityThroughOrm() throws Exception {
        var database = GeneratedEntityBaseline.create("entity_generated_calendar", migrationDirectory);
        var dataSource = database.dataSource();
        for (String kind : new String[]{"DATE", "FLOATING", "UTC", "ZONED"}) {
            write(dataSource, temporal(kind, "scope:alpha", kind));
        }
        // Equal external IDs are distinct persisted identities in separate scopes.
        write(dataSource, temporal("DATE", "scope:beta", "DATE"));
        var entityManager = JpaTestDatabase.entityManager(dataSource);
        assertThat(entityManager.find(CalendarTemporalJpaEntity.class,
                new CalendarEventId("calendar:one", "scope:alpha", "DATE"))).isNotNull();
        assertThat(entityManager.find(CalendarTemporalJpaEntity.class,
                new CalendarEventId("calendar:one", "scope:unknown", "DATE"))).isNull();

        var backwards = temporal("DATE", "scope:alpha", "backwards");
        field(backwards, "endDate", LocalDate.of(2026, 1, 1));
        rejected(dataSource, backwards);
        var missingEnd = temporal("FLOATING", "scope:alpha", "missing-end");
        field(missingEnd, "endLocal", null);
        rejected(dataSource, missingEnd);
        var mixedRepresentation = temporal("UTC", "scope:alpha", "mixed");
        field(mixedRepresentation, "startDate", LocalDate.of(2026, 1, 1));
        rejected(dataSource, mixedRepresentation);
        var missingZone = temporal("ZONED", "scope:alpha", "missing-zone");
        field(missingZone, "zone", null);
        rejected(dataSource, missingZone);
        var unknownKind = temporal("DATE", "scope:alpha", "unknown-kind");
        field(unknownKind, "kind", "INVALID");
        rejected(dataSource, unknownKind);

        var attendee = new CalendarAttendeeJpaEntity();
        field(attendee, "id", new CalendarAttendeeId(id("attendee"), 0));
        rejected(dataSource, attendee); // Neither a member reference nor an address.
        var negativeOrdinal = new CalendarAttendeeJpaEntity();
        field(negativeOrdinal, "id", new CalendarAttendeeId(id("ordinal"), -1));
        field(negativeOrdinal, "address", "test@example.invalid");
        rejected(dataSource, negativeOrdinal);

        var recurrence = recurrence("valid-rule", 1);
        write(dataSource, recurrence);
        rejected(dataSource, recurrence("zero-interval", 0));
        var countAndUntil = recurrence("ambiguous-stop", 1);
        field(countAndUntil, "count", 3);
        field(countAndUntil, "untilLocal", LocalDateTime.of(2026, 2, 1, 12, 0));
        rejected(dataSource, countAndUntil);
        var invalidFrequency = recurrence("frequency", 1);
        field(invalidFrequency, "frequency", "INVALID");
        rejected(dataSource, invalidFrequency);

        assertThat(entityManager.createQuery("select count(t) from CalendarTemporalJpaEntity t", Long.class)
                .getSingleResult()).isEqualTo(5L);
        assertThat(entityManager.createQuery("select count(r) from CalendarRecurrenceRuleJpaEntity r", Long.class)
                .getSingleResult()).isEqualTo(1L);
        assertThat(database.migrations().migrate().migrationsExecuted).isZero();
        JpaTestDatabase.validateSchema(dataSource);
        assertThat(entityManager.find(CalendarTemporalJpaEntity.class,
                new CalendarEventId("calendar:one", "scope:beta", "DATE"))).isNotNull();
    }

    private CalendarTemporalJpaEntity temporal(String kind, String scope, String event) {
        var entity = new CalendarTemporalJpaEntity();
        field(entity, "id", new CalendarEventId("calendar:one", scope, event));
        field(entity, "kind", kind);
        switch (kind) {
            case "DATE" -> {
                field(entity, "startDate", LocalDate.of(2026, 1, 1));
                field(entity, "endDate", LocalDate.of(2026, 1, 2));
            }
            case "FLOATING", "ZONED" -> {
                field(entity, "startLocal", LocalDateTime.of(2026, 1, 1, 12, 0));
                field(entity, "endLocal", LocalDateTime.of(2026, 1, 1, 13, 0));
                if (kind.equals("ZONED")) field(entity, "zone", "Europe/Berlin");
            }
            case "UTC" -> {
                field(entity, "startInstant", OffsetDateTime.parse("2026-01-01T12:00:00Z"));
                field(entity, "endInstant", OffsetDateTime.parse("2026-01-01T13:00:00Z"));
            }
            default -> throw new IllegalArgumentException("Unsupported test kind");
        }
        return entity;
    }

    private CalendarRecurrenceRuleJpaEntity recurrence(String event, int interval) {
        var entity = new CalendarRecurrenceRuleJpaEntity();
        field(entity, "id", id(event));
        field(entity, "frequency", "DAILY");
        field(entity, "interval", interval);
        return entity;
    }

    private CalendarEventId id(String event) {
        return new CalendarEventId("calendar:one", "scope:alpha", event);
    }

    private void field(Object target, String name, Object value) {
        ReflectionTestUtils.setField(target, name, value);
    }

    private void write(DataSource dataSource, Object entity) {
        new TransactionTemplate(JpaTestDatabase.transactionManager(dataSource)).executeWithoutResult(status -> {
            EntityManager manager = JpaTestDatabase.entityManager(dataSource);
            manager.persist(entity);
            manager.flush();
        });
    }

    private void rejected(DataSource dataSource, Object malformedEntity) {
        // Bypass domain validation deliberately; the generated database must enforce the mapping.
        assertThatThrownBy(() -> write(dataSource, malformedEntity)).satisfies(failure -> {
            Throwable cause = NestedExceptionUtils.getMostSpecificCause(failure);
            assertThat(cause).isInstanceOf(SQLException.class);
            assertThat(((SQLException) cause).getSQLState()).isEqualTo("23514");
        });
    }
}
