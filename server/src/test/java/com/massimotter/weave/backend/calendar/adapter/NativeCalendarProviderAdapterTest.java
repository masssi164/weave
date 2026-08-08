package com.massimotter.weave.backend.calendar.adapter;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeCalendarProviderAdapterTest {

    private static final CalendarId CALENDAR = new CalendarId("calendar-tenant-member");
    private static final CalendarScope WORKSPACE = CalendarScope.workspace();
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-03-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void survivesAdapterRestartAndKeepsWritesIdempotentWithDurableEtagsAndSyncTokens() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-restart");
        NativeCalendarProviderAdapter first = adapter(database);
        String emptyToken = first.changes(CALENDAR, WORKSPACE, null).syncToken();
        CalendarEvent proposed = event("planning@weave.test", "Planning");

        CalendarEvent created = first.write(new CalendarWrite(
                proposed, WriteIntent.CREATE, EventVersion.unknown()));
        CalendarEvent duplicateCreate = first.write(new CalendarWrite(
                proposed, WriteIntent.CREATE, EventVersion.unknown()));

        assertThat(created.id()).isEqualTo(proposed.id());
        assertThat(created.version().value()).startsWith("\"weave-calendar-1-").endsWith("\"");
        assertThat(duplicateCreate.version()).isEqualTo(created.version());
        assertThat(first.changes(CALENDAR, WORKSPACE, emptyToken).changes()).singleElement();

        NativeCalendarProviderAdapter afterRestart = adapter(database);
        CalendarEvent restored = afterRestart.read(CALENDAR, WORKSPACE, proposed.id());
        assertThat(restored.id()).isEqualTo(proposed.id());
        assertThat(restored.calendarId()).isEqualTo(CALENDAR);
        assertThat(restored.timezone()).isEqualTo(ZoneId.of("Europe/Berlin"));
        assertThat(restored.attendees()).containsExactlyElementsOf(proposed.attendees());
        assertThat(restored.recurrence()).isEqualTo(proposed.recurrence());

        CalendarEvent changed = withTitle(restored, "Updated planning");
        CalendarEvent updated = afterRestart.write(new CalendarWrite(
                changed, WriteIntent.UPDATE, restored.version()));
        CalendarEvent duplicateUpdate = afterRestart.write(new CalendarWrite(
                changed, WriteIntent.UPDATE, restored.version()));
        assertThat(updated.version().value()).startsWith("\"weave-calendar-2-");
        assertThat(duplicateUpdate.version()).isEqualTo(updated.version());

        assertThatThrownBy(() -> afterRestart.write(new CalendarWrite(
                        withTitle(updated, "Stale overwrite"),
                        WriteIntent.UPDATE,
                        restored.version())))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.type()).isEqualTo(CalendarAdapterException.Type.CONFLICT));

        String beforeDelete = afterRestart.changes(CALENDAR, WORKSPACE, emptyToken).syncToken();
        afterRestart.delete(CALENDAR, WORKSPACE, proposed.id(), updated.version());
        afterRestart.delete(CALENDAR, WORKSPACE, proposed.id(), updated.version());
        assertThat(afterRestart.changes(CALENDAR, WORKSPACE, beforeDelete).changes())
                .singleElement()
                .satisfies(change -> {
                    assertThat(change.eventId()).isEqualTo(proposed.id());
                    assertThat(change.deleted()).isTrue();
                });
        assertThatThrownBy(() -> afterRestart.read(CALENDAR, WORKSPACE, proposed.id()))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.type()).isEqualTo(CalendarAdapterException.Type.NOT_FOUND));
    }

    @Test
    void preservesWallClockRecurrenceAcrossDstAndPersistsRdatesExdatesAndAttendees() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-recurrence");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent recurring = event("dst@weave.test", "DST planning");
        adapter.write(new CalendarWrite(recurring, WriteIntent.CREATE, EventVersion.unknown()));

        Instant from = Instant.parse("2026-03-27T00:00:00Z");
        Instant to = Instant.parse("2026-04-03T00:00:00Z");
        CalendarEvent restored = adapter.query(CALENDAR, WORKSPACE, from, to).getFirst();
        assertThat(restored.recurrence()).isEqualTo(recurring.recurrence());
        assertThat(restored.attendees()).containsExactlyElementsOf(recurring.attendees());
        assertThat(adapter.freeBusy(CALENDAR, WORKSPACE, from, to))
                .extracting(window -> window.start().toString())
                .containsExactly(
                        "2026-03-28T08:00:00Z",
                        "2026-03-29T07:00:00Z",
                        "2026-03-31T07:00:00Z",
                        "2026-04-02T07:00:00Z");
    }

    @Test
    void isolatesCalendarAndScopeStateIncludingNativeSyncTokens() {
        DataSource database = JpaTestDatabase.entityFirstDataSource("native-calendar-isolation");
        NativeCalendarProviderAdapter adapter = adapter(database);
        CalendarEvent created = adapter.write(new CalendarWrite(
                event("isolated@weave.test", "Isolated"),
                WriteIntent.CREATE,
                EventVersion.unknown()));
        String token = adapter.changes(CALENDAR, WORKSPACE, null).syncToken();
        CalendarScope otherScope = new CalendarScope(ScopeType.TEAM, "engineering", null);
        CalendarId otherCalendar = new CalendarId("calendar-other-tenant-member");

        assertThat(token)
                .startsWith(NativeCalendarProviderAdapter.SYNC_TOKEN_PREFIX)
                .doesNotContain(created.id().value())
                .doesNotContain("provider")
                .doesNotContain("http");
        assertThatThrownBy(() -> adapter.read(CALENDAR, otherScope, created.id()))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.type()).isEqualTo(CalendarAdapterException.Type.NOT_FOUND));
        assertThatThrownBy(() -> adapter.changes(CALENDAR, otherScope, token))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.type()).isEqualTo(CalendarAdapterException.Type.CONFLICT));
        assertThatThrownBy(() -> adapter.changes(otherCalendar, WORKSPACE, token))
                .isInstanceOfSatisfying(CalendarAdapterException.class,
                        failure -> assertThat(failure.type()).isEqualTo(CalendarAdapterException.Type.CONFLICT));
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
                        List.of(ZonedDateTime.of(
                                LocalDateTime.parse("2026-04-02T09:00:00"), timezone)),
                        List.of(ZonedDateTime.of(
                                LocalDateTime.parse("2026-03-30T09:00:00"), timezone))),
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
                existing.localStart(),
                existing.localEnd(),
                existing.timezone(),
                existing.allDay(),
                existing.location(),
                existing.attendees(),
                existing.recurrence(),
                existing.version(),
                existing.updatedAt());
    }
}
