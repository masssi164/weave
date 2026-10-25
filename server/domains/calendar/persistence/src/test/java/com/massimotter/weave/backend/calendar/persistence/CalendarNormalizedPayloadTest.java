package com.massimotter.weave.backend.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CalendarNormalizedPayloadTest {
    private static final CalendarEventId EVENT = new CalendarEventId("calendar", "workspace:one", "event");

    @ParameterizedTest
    @EnumSource(TemporalKind.class)
    void retainsTemporalShapeAndCancelledAndMovedOverridePayloads(TemporalKind kind) {
        TemporalValue start = temporal(kind, "2026-03-28T09:00:00");
        TemporalValue end = temporal(kind, "2026-03-29T10:00:00");
        CalendarTemporalJpaEntity pair = new CalendarTemporalJpaEntity(EVENT, start, end);
        assertThat(pair.start()).isEqualTo(start);
        assertThat(pair.end()).isEqualTo(end);
        assertThat(new CalendarRecurrenceDateJpaEntity(new CalendarRecurrenceDateId(EVENT, "EXDATE", 0), start).toDomain())
                .isEqualTo(start);
        RecurrenceOverride cancelled = new RecurrenceOverride(start, null, null, true, null, null, null);
        RecurrenceOverride moved = new RecurrenceOverride(start, start, end, false, "Moved", "Description", "Room");
        assertThat(new CalendarOverrideJpaEntity(new CalendarOverrideId(EVENT, "cancelled"), cancelled).toDomain())
                .isEqualTo(cancelled);
        assertThat(new CalendarOverrideJpaEntity(new CalendarOverrideId(EVENT, "moved"), moved).toDomain())
                .isEqualTo(moved);
    }

    @Test
    void retainsRecurrenceZoneListsAndAttendeeNullSemantics() {
        var until = LocalDateTime.parse("2026-10-25T09:30:00").atZone(ZoneId.of("Europe/Berlin"));
        var recurrence = new RecurrenceSet(RecurrenceFrequency.MONTHLY, 2, null, until, List.of(), List.of(),
                List.of("MO", "-1FR"), List.of(1, -1), List.of(3, 10), List.of(-1), "SU");
        assertThat(new CalendarRecurrenceRuleJpaEntity(EVENT, recurrence).toDomain(List.of(), List.of())).isEqualTo(recurrence);
        var attendee = new Attendee(null, null, "member@example.test", null, null);
        assertThat(new CalendarAttendeeJpaEntity(new CalendarAttendeeId(EVENT, 0), attendee).toDomain()).isEqualTo(attendee);
    }

    private TemporalValue temporal(TemporalKind kind, String input) {
        LocalDateTime local = LocalDateTime.parse(input);
        return switch (kind) {
            case DATE -> TemporalValue.date(local.toLocalDate());
            case FLOATING -> TemporalValue.floating(local);
            case UTC -> TemporalValue.utc(local.toInstant(ZoneOffset.UTC));
            case ZONED -> TemporalValue.zoned(local, ZoneId.of("Europe/Berlin"));
        };
    }
}
