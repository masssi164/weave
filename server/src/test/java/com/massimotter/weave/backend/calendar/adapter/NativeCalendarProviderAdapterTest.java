package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class NativeCalendarProviderAdapterTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-20T08:00:00Z"), ZoneOffset.UTC);
    private static final CalendarId CALENDAR = new CalendarId("workspace-main");
    private static final CalendarScope WORKSPACE = CalendarScope.workspace();

    @Test
    void canonicalWritesSurviveRestartAndKeepStableVersions() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-restart-v2");
        NativeCalendarProviderAdapter first = adapter(database);
        CalendarEvent created = first.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));
        NativeCalendarProviderAdapter restarted = adapter(database);

        assertThat(restarted.read(CALENDAR, WORKSPACE, created.id()).title()).isEqualTo("Planning");
        assertThat(restarted.read(CALENDAR, WORKSPACE, created.id()).version()).isEqualTo(created.version());
    }

    @Test
    void updateRequiresMatchingVersionAndDeleteIsDurable() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-update-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent created = adapter.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));

        assertThatThrownBy(() -> adapter.write(new CalendarWrite(
                withTitle(created, "Wrong"), WriteIntent.UPDATE, new EventVersion("\"stale\""))))
                .isInstanceOf(RuntimeException.class);

        CalendarEvent updated = adapter.write(new CalendarWrite(withTitle(created, "Updated"), WriteIntent.UPDATE, created.version()));
        adapter.delete(CALENDAR, WORKSPACE, updated.id(), updated.version());

        assertThatThrownBy(() -> adapter.read(CALENDAR, WORKSPACE, updated.id())).isInstanceOf(RuntimeException.class);
    }

    @Test
    void queryAndFreeBusyUseBoundedIcal4jRecurrenceWithRdateAndExdate() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-recurrence-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        adapter.write(new CalendarWrite(event("planning", "Planning"), WriteIntent.CREATE, EventVersion.unknown()));

        Instant from = Instant.parse("2026-03-27T00:00:00Z");
        Instant to = Instant.parse("2026-04-04T00:00:00Z");

        assertThat(adapter.query(CALENDAR, WORKSPACE, from, to)).singleElement();
        assertThat(adapter.freeBusy(CALENDAR, WORKSPACE, from, to))
                .extracting(window -> window.start().atZone(ZoneId.of("Europe/Berlin")).toLocalDate().toString())
                .containsExactly("2026-03-28", "2026-03-29", "2026-03-31", "2026-04-02");
    }

    @Test
    void syncTokensAreScopeBoundAndSnapshotBounded() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-sync-v2");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent first = adapter.write(new CalendarWrite(event("one", "One"), WriteIntent.CREATE, EventVersion.unknown()));
        var initial = adapter.changes(CALENDAR, WORKSPACE, null);
        adapter.write(new CalendarWrite(event("two", "Two"), WriteIntent.CREATE, EventVersion.unknown()));

        assertThat(initial.changes()).extracting(change -> change.eventId().value()).containsExactly(first.id().value());
        assertThat(adapter.changes(CALENDAR, WORKSPACE, initial.syncToken()).changes())
                .extracting(change -> change.eventId().value()).containsExactly("two");
    }

    private NativeCalendarProviderAdapter adapter(DataSource database) {
        NativeCalendarProviderAdapter target = new NativeCalendarProviderAdapter(
                JpaTestDatabase.repository(database, CalendarCollectionJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarEventJpaRepository.class),
                JpaTestDatabase.repository(database, CalendarChangeJpaRepository.class),
                CLOCK);
        return JpaTestDatabase.transactional(database, target);
    }

    private CalendarEvent event(String id, String title) {
        ZoneId timezone = ZoneId.of("Europe/Berlin");
        return new CalendarEvent(
                CALENDAR,
                new EventId(id),
                WORKSPACE,
                title,
                "Canonical native event",
                LocalDateTime.parse("2026-03-28T09:00:00"),
                LocalDateTime.parse("2026-03-28T10:00:00"),
                timezone,
                false,
                "Workspace room",
                List.of(new Attendee(
                        "member:alex",
                        "Alex",
                        "mailto:alex@example.test",
                        "REQ-PARTICIPANT",
                        "ACCEPTED")),
                new RecurrenceSet(
                        RecurrenceFrequency.DAILY,
                        1,
                        4,
                        null,
                        List.of(TemporalValue.zoned(LocalDateTime.parse("2026-04-02T09:00:00"), timezone)),
                        List.of(TemporalValue.zoned(LocalDateTime.parse("2026-03-30T09:00:00"), timezone))),
                EventVersion.unknown(),
                null);
    }

    private CalendarEvent withTitle(CalendarEvent existing, String title) {
        return new CalendarEvent(
                existing.calendarId(),
                existing.id(),
                existing.scope(),
                title,
                existing.description(),
                existing.startValue(),
                existing.endValue(),
                existing.location(),
                existing.attendees(),
                existing.recurrence(),
                existing.overrides(),
                existing.version(),
                existing.updatedAt());
    }
}
