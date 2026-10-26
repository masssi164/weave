package com.massimotter.weave.backend.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import java.time.Instant;
import java.time.Clock;
import org.hibernate.SessionFactory;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.massimotter.weave.backend.calendar.domain.CalendarOccurrenceEngine;
import com.massimotter.weave.backend.calendar.provider.icalendar.Ical4jRecurrenceEngine;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.assertj.AssertableWebApplicationContext;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real owning mappings, serving imports and provider transactions; no App or all-domain fixture. */
@Tag("postgres")
@Testcontainers
class CalendarCandidateWindowPostgresTest {
    @Container
    static final PostgreSQLContainer<?> DATABASE = new PostgreSQLContainer<>("postgres:16.9-alpine");
    private static final CalendarId CALENDAR = new CalendarId("calendar:tenant-a");
    private static final CalendarScope SCOPE = CalendarScope.workspace();
    private static final String SCOPE_KEY = "WORKSPACE::";

    @Test
    void dateCandidatesIncludeSameDayAndRespectExclusiveMidnight() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            var store = context.getBean(NativeCalendarRelationalStore.class);
            write(provider, event("all-day", date("2026-01-15"), date("2026-01-16")));
            write(provider, event("previous-day", date("2026-01-14"), date("2026-01-15")));
            write(provider, event("next-day", date("2026-01-16"), date("2026-01-17")));
            Instant from = Instant.parse("2026-01-15T12:00:00Z");
            Instant to = Instant.parse("2026-01-15T13:00:00Z");
            assertAll(
                    () -> assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, ZoneOffset.UTC))
                            .containsExactly("all-day"),
                    () -> assertWindow(provider, from, to, "all-day", Instant.parse("2026-01-15T00:00:00Z"),
                            Instant.parse("2026-01-16T00:00:00Z")));
            assertWindow(provider, Instant.parse("2026-01-15T23:00:00Z"), Instant.parse("2026-01-16T00:00:00Z"),
                    "all-day", Instant.parse("2026-01-15T00:00:00Z"), Instant.parse("2026-01-16T00:00:00Z"));
            assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY,
                    Instant.parse("2026-01-14T23:30:00Z"), Instant.parse("2026-01-15T00:00:00Z"), ZoneId.of("Europe/Berlin")))
                    .containsExactly("all-day");
            assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY,
                    Instant.parse("2026-01-14T23:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC))
                    .containsExactly("previous-day");
            assertWindow(provider, Instant.parse("2026-01-14T23:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"),
                    "previous-day", Instant.parse("2026-01-14T00:00:00Z"), Instant.parse("2026-01-15T00:00:00Z"));
            assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY,
                    Instant.parse("2026-01-16T00:00:00Z"), Instant.parse("2026-01-16T01:00:00Z"), ZoneOffset.UTC))
                    .containsExactly("next-day");
            assertWindow(provider, Instant.parse("2026-01-16T00:00:00Z"), Instant.parse("2026-01-16T01:00:00Z"),
                    "next-day", Instant.parse("2026-01-16T00:00:00Z"), Instant.parse("2026-01-17T00:00:00Z"));
        });
    }

    static Stream<Arguments> zonedWindows() {
        return Stream.of(
                Arguments.of("Europe/Berlin", "2026-01-15T09:00", "2026-01-15T10:00"),
                Arguments.of("America/Los_Angeles", "2026-01-15T09:00", "2026-01-15T10:00"),
                Arguments.of("Pacific/Kiritimati", "2026-01-15T09:00", "2026-01-15T10:00"),
                Arguments.of("Pacific/Pago_Pago", "2026-01-15T09:00", "2026-01-15T10:00"),
                // java.time resolves nonexistent times forward and overlaps to the earlier offset.
                Arguments.of("Europe/Berlin", "2026-03-29T02:15", "2026-03-29T04:15"),
                Arguments.of("Europe/Berlin", "2026-10-25T02:15", "2026-10-25T03:15"));
    }

    @ParameterizedTest
    @MethodSource("zonedWindows")
    void zonedCandidatesUseInstantOverlapAcrossOffsetsGapAndOverlap(String zone, String start, String end) {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            var store = context.getBean(NativeCalendarRelationalStore.class);
            TemporalValue first = TemporalValue.zoned(LocalDateTime.parse(start), ZoneId.of(zone));
            TemporalValue last = TemporalValue.zoned(LocalDateTime.parse(end), ZoneId.of(zone));
            write(provider, event("zoned", first, last));
            write(provider, event("far-away", TemporalValue.zoned(LocalDateTime.parse(start).plusDays(7), ZoneId.of(zone)),
                    TemporalValue.zoned(LocalDateTime.parse(end).plusDays(7), ZoneId.of(zone))));
            Instant from = first.toInstant(ZoneOffset.UTC).plusSeconds(15 * 60);
            Instant to = first.toInstant(ZoneOffset.UTC).plusSeconds(30 * 60);
            assertAll(
                    () -> assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, ZoneOffset.UTC))
                            .containsExactly("zoned"),
                    () -> assertWindow(provider, from, to, "zoned", first.toInstant(ZoneOffset.UTC), last.toInstant(ZoneOffset.UTC)));
            Instant before = first.toInstant(ZoneOffset.UTC);
            assertThat(provider.query(CALENDAR, SCOPE, before.minusSeconds(60), before)).isEmpty();
            assertThat(provider.freeBusy(CALENDAR, SCOPE, before.minusSeconds(60), before)).isEmpty();
            Instant after = last.toInstant(ZoneOffset.UTC);
            assertThat(provider.query(CALENDAR, SCOPE, after, after.plusSeconds(60))).isEmpty();
            assertThat(provider.freeBusy(CALENDAR, SCOPE, after, after.plusSeconds(60))).isEmpty();
        });
    }

    @Test
    void floatingUtcAndDatabaseSupersetRetainExactFilteringAndScopedIdentity() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            var store = context.getBean(NativeCalendarRelationalStore.class);
            Instant from = Instant.parse("2026-06-10T12:00:00Z");
            Instant to = from.plusSeconds(3600);
            write(provider, event("floating", TemporalValue.floating(LocalDateTime.parse("2026-06-10T12:00")),
                    TemporalValue.floating(LocalDateTime.parse("2026-06-10T13:00"))));
            write(provider, event("utc", TemporalValue.utc(from), TemporalValue.utc(to)));
            var excluded = event("superset-only", TemporalValue.zoned(LocalDateTime.parse("2026-06-10T01:00"), ZoneId.of("UTC")),
                    TemporalValue.zoned(LocalDateTime.parse("2026-06-10T02:00"), ZoneId.of("UTC")));
            write(provider, excluded);
            var other = event("utc", TemporalValue.utc(from), TemporalValue.utc(to));
            write(provider, new CalendarEvent(new CalendarId("calendar:tenant-b"), other.id(), SCOPE, other.title(), null,
                    other.startValue(), other.endValue(), null, List.of(), null, List.of(), null, null));
            write(provider, new CalendarEvent(CALENDAR, other.id(), new CalendarScope(ScopeType.TEAM, "team-b", null),
                    other.title(), null, other.startValue(), other.endValue(), null, List.of(), null, List.of(), null, null));
            assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, ZoneOffset.UTC))
                    .contains("floating", "utc");
            assertThat(provider.query(CALENDAR, SCOPE, from, to)).extracting(value -> value.id().value())
                    .containsExactly("floating", "utc");
            assertThat(provider.freeBusy(CALENDAR, SCOPE, from, to)).containsExactly(
                    new FreeBusyWindow(from, to), new FreeBusyWindow(from, to));
            assertThat(provider.query(CALENDAR, SCOPE, to, to.plusSeconds(60))).isEmpty();
            var written = provider.read(CALENDAR, SCOPE, new EventId("utc"));
            provider.delete(CALENDAR, SCOPE, written.id(), written.version());
            assertThat(store.candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, ZoneOffset.UTC))
                    .doesNotContain("utc");
        });
    }

    @Test
    void movedAndCancelledOverridesKeepExactRecurrenceAndFreeBusySemantics() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            var recurrence = new RecurrenceSet(RecurrenceFrequency.DAILY, 1, 3, null,
                    List.of(date("2026-06-12"), date("2026-06-14")), List.of(date("2026-06-01")));
            var event = new CalendarEvent(CALENDAR, new EventId("recurring"), SCOPE, "Recurring", null,
                    date("2026-06-01"), date("2026-06-02"), null,
                    List.of(new Attendee("member:one", "One", null, "required", "accepted")), recurrence,
                    List.of(new RecurrenceOverride(date("2026-06-02"), date("2026-06-10"), date("2026-06-11"), false, null, null, null),
                            new RecurrenceOverride(date("2026-06-03"), null, null, true, null, null, null)), null, null);
            write(provider, event);
            var queried = provider.query(CALENDAR, SCOPE, Instant.parse("2026-06-10T09:00:00Z"), Instant.parse("2026-06-10T10:00:00Z")).getFirst();
            assertThat(queried.recurrence()).isEqualTo(recurrence);
            assertThat(queried.attendees()).isEqualTo(event.attendees());
            assertThat(queried.overrides()).containsExactlyElementsOf(event.overrides());
            assertThat(provider.query(CALENDAR, SCOPE, Instant.parse("2026-06-01T00:00:00Z"), Instant.parse("2026-06-02T00:00:00Z"))).isEmpty();
            assertWindow(provider, Instant.parse("2026-06-12T09:00:00Z"), Instant.parse("2026-06-12T10:00:00Z"),
                    "recurring", Instant.parse("2026-06-12T00:00:00Z"), Instant.parse("2026-06-13T00:00:00Z"));
            assertWindow(provider, Instant.parse("2026-06-10T09:00:00Z"), Instant.parse("2026-06-10T10:00:00Z"),
                    "recurring", Instant.parse("2026-06-10T00:00:00Z"), Instant.parse("2026-06-11T00:00:00Z"));
            for (var window : List.of(
                    new FreeBusyWindow(Instant.parse("2026-06-09T23:00:00Z"), Instant.parse("2026-06-10T00:00:00Z")),
                    new FreeBusyWindow(Instant.parse("2026-06-11T00:00:00Z"), Instant.parse("2026-06-11T01:00:00Z")))) {
                assertThat(provider.query(CALENDAR, SCOPE, window.start(), window.end())).isEmpty();
                assertThat(provider.freeBusy(CALENDAR, SCOPE, window.start(), window.end())).isEmpty();
            }
            assertThat(provider.query(CALENDAR, SCOPE, Instant.parse("2026-06-03T00:00:00Z"),
                    Instant.parse("2026-06-04T00:00:00Z"))).isEmpty();
        });
    }

    @Test
    void movedZonedOverrideKeepsItsOwnZoneAcrossDstAndScopeBoundaries() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            ZoneId zone = ZoneId.of("Europe/Berlin");
            TemporalValue original = TemporalValue.zoned(LocalDateTime.parse("2026-03-28T09:00"), zone);
            TemporalValue movedStart = TemporalValue.zoned(LocalDateTime.parse("2026-03-29T02:15"), zone);
            TemporalValue movedEnd = TemporalValue.zoned(LocalDateTime.parse("2026-03-29T04:15"), zone);
            var recurrence = new RecurrenceSet(RecurrenceFrequency.DAILY, 1, 2, null, List.of(), List.of());
            var override = new RecurrenceOverride(original, movedStart, movedEnd, false, "Moved", null, null);
            write(provider, new CalendarEvent(CALENDAR, new EventId("moved-zoned"), SCOPE, "Recurring", null,
                    original, TemporalValue.zoned(LocalDateTime.parse("2026-03-28T10:00"), zone), null,
                    List.of(), recurrence, List.of(override), null, null));
            Instant from = movedStart.toInstant(ZoneOffset.UTC).plusSeconds(60);
            Instant to = movedEnd.toInstant(ZoneOffset.UTC).minusSeconds(60);
            assertThat(context.getBean(NativeCalendarRelationalStore.class)
                    .candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, ZoneOffset.UTC)).containsExactly("moved-zoned");
            assertWindow(provider, from, to, "moved-zoned", movedStart.toInstant(ZoneOffset.UTC), movedEnd.toInstant(ZoneOffset.UTC));
            assertThat(provider.query(CALENDAR, new CalendarScope(ScopeType.TEAM, "other", null), from, to)).isEmpty();
            assertThat(provider.freeBusy(new CalendarId("other-calendar"), SCOPE, from, to)).isEmpty();
        });
    }

    @Test
    void floatingOverlapAcrossDstFallBackUsesExplicitEvaluationZone() {
        database(context -> {
            ZoneId evaluation = ZoneId.of("Europe/Berlin");
            var provider = new NativeCalendarProviderAdapter(context.getBean(CalendarCollectionJpaRepository.class),
                    context.getBean(CalendarEventJpaRepository.class), context.getBean(CalendarChangeJpaRepository.class),
                    context.getBean(CalendarSnapshotChangeRepository.class), context.getBean(NativeCalendarRelationalStore.class),
                    new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()), evaluation, Clock.systemUTC());
            var first = TemporalValue.floating(LocalDateTime.parse("2026-10-25T02:30"));
            var last = TemporalValue.floating(LocalDateTime.parse("2026-10-25T02:50"));
            var transaction = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            transaction.executeWithoutResult(status -> write(provider, event("floating-overlap", first, last)));
            Instant from = Instant.parse("2026-10-25T00:45:00Z");
            Instant to = Instant.parse("2026-10-25T01:15:00Z");
            transaction.executeWithoutResult(status -> assertAll(
                    () -> assertThat(context.getBean(NativeCalendarRelationalStore.class)
                            .candidateEventIds(CALENDAR.value(), SCOPE_KEY, from, to, evaluation)).containsExactly("floating-overlap"),
                    () -> assertWindow(provider, from, to, "floating-overlap", first.toInstant(evaluation), last.toInstant(evaluation))));
        });
    }

    @Test
    void databaseTieOrderingRetainsPostgresCollation() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            Instant from = Instant.parse("2026-06-10T12:00:00Z");
            String supplementary = "event-" + new String(Character.toChars(0x10000));
            String basicMultilingual = "event-" + Character.toString(0xe000);
            write(provider, event(supplementary, TemporalValue.utc(from), TemporalValue.utc(from.plusSeconds(60))));
            write(provider, event(basicMultilingual, TemporalValue.utc(from), TemporalValue.utc(from.plusSeconds(60))));
            var databaseOrder = context.getBean(CalendarEventJpaRepository.class).findActive(CALENDAR.value(), SCOPE_KEY)
                    .stream().map(row -> row.toDomain().id().value()).toList();
            assertThat(provider.query(CALENDAR, SCOPE, from, from.plusSeconds(3600)))
                    .extracting(value -> value.id().value()).containsExactlyElementsOf(databaseOrder);
        });
    }

    @Test
    void candidatePayloadsAreBatchedWithoutTruncationOrPerEventQueries() {
        database(context -> {
            var provider = context.getBean(NativeCalendarProviderAdapter.class);
            Instant from = Instant.parse("2026-06-10T12:00:00Z");
            for (int ordinal = 0; ordinal < 205; ordinal++) {
                Instant start = from.plusSeconds(ordinal);
                write(provider, event("event-" + (1000 - ordinal), TemporalValue.utc(start), TemporalValue.utc(start.plusSeconds(60))));
            }
            write(provider, event("not-a-candidate", TemporalValue.utc(from.plusSeconds(604800)),
                    TemporalValue.utc(from.plusSeconds(604860))));
            var statistics = context.getBean(EntityManagerFactory.class).unwrap(SessionFactory.class).getStatistics();
            statistics.setStatisticsEnabled(true);
            statistics.clear();
            var queried = provider.query(CALENDAR, SCOPE, from, from.plusSeconds(3600));
            assertThat(queried).hasSize(205);
            assertThat(queried.getFirst().id().value()).isEqualTo("event-1000");
            assertThat(queried.getLast().id().value()).isEqualTo("event-796");
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(20);
            assertThat(statistics.getEntityLoadCount()).isEqualTo(410);
            statistics.clear();
            assertThat(provider.freeBusy(CALENDAR, SCOPE, from, from.plusSeconds(3600))).hasSize(205);
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(20);
        });
    }

    private void database(Consumer<AssertableWebApplicationContext> checks) {
        new WebApplicationContextRunner().withUserConfiguration(Discovery.class)
                .withPropertyValues("spring.datasource.url=" + DATABASE.getJdbcUrl(),
                        "spring.datasource.username=" + DATABASE.getUsername(), "spring.datasource.password=" + DATABASE.getPassword(),
                        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.flyway.enabled=false", "spring.jpa.open-in-view=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    checks.accept(context);
                });
    }

    private static void write(NativeCalendarProviderAdapter provider, CalendarEvent event) {
        provider.write(new CalendarWrite(event, WriteIntent.CREATE, null));
    }

    private static CalendarEvent event(String id, TemporalValue start, TemporalValue end) {
        return new CalendarEvent(CALENDAR, new EventId(id), SCOPE, id, null, start, end, null,
                List.of(), null, List.of(), null, null);
    }

    private static TemporalValue date(String value) {
        return TemporalValue.date(LocalDate.parse(value));
    }

    private static void assertWindow(NativeCalendarProviderAdapter provider, Instant from, Instant to,
            String id, Instant expectedStart, Instant expectedEnd) {
        assertAll(
                () -> assertThat(provider.query(CALENDAR, SCOPE, from, to)).extracting(value -> value.id().value()).containsExactly(id),
                () -> assertThat(provider.freeBusy(CALENDAR, SCOPE, from, to)).containsExactly(new FreeBusyWindow(expectedStart, expectedEnd)));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    static class Discovery {}
}
