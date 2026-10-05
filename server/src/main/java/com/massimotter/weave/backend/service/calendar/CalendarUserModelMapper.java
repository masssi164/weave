package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;

final class CalendarUserModelMapper {
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss");
    private CalendarUserModelMapper() {}

    static CalendarEvent event(CalendarId calendar, EventId id, CalendarScope scope, WriteRequest value) {
        TemporalValue start = temporal(value.start());
        var recurrence = value.recurrence();
        RecurrenceSet rule = recurrence == null ? null : new RecurrenceSet(recurrence.frequency(), recurrence.interval(),
                recurrence.count(), until(recurrence.until(), start.kind()),
                recurrence.additionalDates().stream().map(CalendarUserModelMapper::temporal).toList(),
                recurrence.excludedDates().stream().map(CalendarUserModelMapper::temporal).toList(),
                recurrence.byDay(), recurrence.byMonthDay(), recurrence.byMonth(), recurrence.bySetPos(), recurrence.weekStart());
        CalendarEvent event = new CalendarEvent(calendar, id, scope, value.title(), value.description(), start,
                temporal(value.end()), value.location(),
                value.attendees().stream().map(a -> new CalendarDomain.Attendee(null, a.displayName(), a.address(), a.role(), a.response())).toList(),
                rule, value.overrides().stream().map(o -> new RecurrenceOverride(temporal(o.recurrenceId()),
                        o.start() == null ? null : temporal(o.start()), o.end() == null ? null : temporal(o.end()),
                        o.cancelled(), o.title(), o.description(), o.location())).toList(), EventVersion.unknown(), Instant.EPOCH);
        // The codec is the profile validator, including recurrence grammar and parameters.
        String encoded = new Ical4jIcalendarCodec().encode(event);
        CalendarEvent roundTrip = new Ical4jIcalendarCodec().decode(calendar, scope, event.version(), encoded);
        if (!content(event).equals(content(roundTrip))) {
            throw new IllegalArgumentException("Calendar content is outside the lossless supported profile");
        }
        return event;
    }

    static TemporalValue temporal(TimeValue value) {
        if (value == null || value.kind() == null) throw new IllegalArgumentException("Calendar time is required");
        if (value.localDateTime() != null && !value.localDateTime().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("Calendar local time must preserve second precision without an offset");
        }
        if (value.instant() != null && !value.instant().matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")) {
            throw new IllegalArgumentException("Calendar UTC time must be a second-precision UTC instant");
        }
        ZoneId zone = value.timeZone() == null ? null : zone(value.timeZone());
        return new TemporalValue(value.kind(), value.date() == null ? null : LocalDate.parse(value.date()),
                value.localDateTime() == null ? null : LocalDateTime.parse(value.localDateTime()),
                value.instant() == null ? null : Instant.parse(value.instant()), zone);
    }

    static ZoneId zone(String value) {
        if (value == null || !ZoneId.getAvailableZoneIds().contains(value)) throw new IllegalArgumentException("An IANA timezone is required");
        return ZoneId.of(value);
    }

    private static ZonedDateTime until(TimeValue value, TemporalKind startKind) {
        if (value == null) return null;
        TemporalValue parsed = temporal(value);
        TemporalKind expected = startKind == TemporalKind.ZONED ? TemporalKind.UTC : startKind;
        if (parsed.kind() != expected) throw new IllegalArgumentException("UNTIL must match DTSTART semantics");
        return switch (parsed.kind()) {
            case DATE -> parsed.date().atStartOfDay(ZoneOffset.UTC);
            case FLOATING -> parsed.localDateTime().atZone(ZoneOffset.UTC);
            case UTC -> parsed.instant().atZone(ZoneOffset.UTC);
            case ZONED -> throw new IllegalArgumentException("UNTIL must use UTC for a zoned event");
        };
    }

    static TimeValue time(TemporalValue value) {
        return new TimeValue(value.kind(), value.date() == null ? null : value.date().toString(),
                value.localDateTime() == null ? null : LOCAL.format(value.localDateTime()),
                value.instant() == null ? null : value.instant().toString(), value.zoneId() == null ? null : value.zoneId().getId());
    }

    static WriteRequest content(CalendarEvent event) {
        RecurrenceSet r = event.recurrence();
        TimeValue until = r == null || r.until() == null ? null : switch (event.startValue().kind()) {
            case DATE -> time(TemporalValue.date(r.until().toLocalDate()));
            case FLOATING -> time(TemporalValue.floating(r.until().toLocalDateTime()));
            case UTC, ZONED -> time(TemporalValue.utc(r.until().toInstant()));
        };
        Recurrence recurrence = r == null ? null : new Recurrence(r.frequency(), r.interval(), r.count(), until,
                r.additionalDates().stream().map(CalendarUserModelMapper::time).toList(),
                r.excludedDates().stream().map(CalendarUserModelMapper::time).toList(),
                r.byDay(), r.byMonthDay(), r.byMonth(), r.bySetPos(), r.weekStart());
        return new WriteRequest(event.title(), event.description(), time(event.startValue()), time(event.endValue()), event.location(),
                event.attendees().stream().map(a -> new CalendarUserModels.Attendee(a.displayName(), a.address(), a.role(), a.response())).toList(),
                recurrence, event.overrides().stream().map(o -> new CalendarUserModels.Override(time(o.recurrenceId()),
                        o.start() == null ? null : time(o.start()), o.end() == null ? null : time(o.end()),
                        o.cancelled(), o.title(), o.description(), o.location())).toList());
    }
}
