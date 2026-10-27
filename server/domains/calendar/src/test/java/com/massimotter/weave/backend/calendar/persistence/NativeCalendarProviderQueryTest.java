package com.massimotter.weave.backend.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.domain.CalendarOccurrenceEngine;
import com.massimotter.weave.backend.calendar.provider.icalendar.Ical4jRecurrenceEngine;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeCalendarProviderQueryTest {
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-20T08:00:00Z"), ZoneOffset.UTC);
    private static final CalendarId CALENDAR = new CalendarId("workspace-main");
    private static final CalendarScope WORKSPACE = CalendarScope.workspace();

    @Test
    void boundedQueryFetchesOnlyDatabaseCandidatesBeforeEnriching() {
        CalendarEventJpaRepository events = mock(CalendarEventJpaRepository.class);
        NativeCalendarRelationalStore store = mock(NativeCalendarRelationalStore.class);
        Instant from = Instant.parse("2026-03-28T00:00:00Z");
        Instant to = Instant.parse("2026-03-29T00:00:00Z");
        CalendarEvent event = event("candidate", "Candidate");
        CalendarEventJpaEntity row = CalendarEventJpaEntity.create(new CalendarEventId(CALENDAR.value(), "WORKSPACE::", "candidate"));
        row.apply(event, 1, "version", CLOCK.instant());
        when(store.candidateEventIds(CALENDAR.value(), "WORKSPACE::", from, to, ZoneOffset.UTC))
                .thenReturn(List.of("candidate"));
        when(events.findActiveCandidates(CALENDAR.value(), "WORKSPACE::", List.of("candidate"))).thenReturn(List.of(row));
        when(store.enrichEvents(any(), eq("WORKSPACE::"))).thenAnswer(invocation -> invocation.getArgument(0));
        NativeCalendarProviderAdapter adapter = new NativeCalendarProviderAdapter(
                mock(CalendarCollectionJpaRepository.class), events, mock(CalendarChangeJpaRepository.class),
                mock(CalendarSnapshotChangeRepository.class), store,
                new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()), ZoneOffset.UTC, CLOCK);

        assertThat(adapter.query(CALENDAR, WORKSPACE, from, to)).extracting(value -> value.id().value()).containsExactly("candidate");
        verify(events, never()).findActive(anyString(), anyString());
        verify(events).findActiveCandidates(CALENDAR.value(), "WORKSPACE::", List.of("candidate"));
        verify(store).enrichEvents(any(), eq("WORKSPACE::"));
    }

    @Test
    void emptyDatabaseCandidatesAvoidLoadingEventPayloads() {
        CalendarEventJpaRepository events = mock(CalendarEventJpaRepository.class);
        NativeCalendarRelationalStore store = mock(NativeCalendarRelationalStore.class);
        NativeCalendarProviderAdapter adapter = new NativeCalendarProviderAdapter(
                mock(CalendarCollectionJpaRepository.class), events, mock(CalendarChangeJpaRepository.class),
                mock(CalendarSnapshotChangeRepository.class), store,
                new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine()), ZoneOffset.UTC, CLOCK);

        assertThat(adapter.query(CALENDAR, WORKSPACE,
                Instant.parse("2026-03-28T00:00:00Z"), Instant.parse("2026-03-29T00:00:00Z"))).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(events);
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

}
