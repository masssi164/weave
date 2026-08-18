package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarOccurrence;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceOverride;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Canonical bounded occurrence projection backed exclusively by {@link RecurrenceEngine}.
 * RFC recurrence grammar remains inside the iCal4j adapter. FLOATING and DATE values are
 * interpreted only for a query using the explicit evaluation zone supplied by the caller;
 * their stored/serialized temporal semantics are never rewritten.
 */
public final class CalendarOccurrenceEngine {

    private static final int MAX_RESULTS = 10_000;
    private final RecurrenceEngine recurrenceEngine;

    public CalendarOccurrenceEngine(RecurrenceEngine recurrenceEngine) {
        this.recurrenceEngine = Objects.requireNonNull(recurrenceEngine, "recurrenceEngine");
    }

    public List<CalendarOccurrence> occurrences(
            CalendarEvent event,
            Instant from,
            Instant to,
            ZoneId evaluationZone) {
        Objects.requireNonNull(event, "event");
        Objects.requireNonNull(evaluationZone, "evaluationZone");
        if (from == null || to == null || !from.isBefore(to)) {
            throw new IllegalArgumentException("calendar occurrence window must be bounded");
        }
        Map<String, CalendarOccurrence> values = new LinkedHashMap<>();
        if (event.recurrence() == null) {
            addIfOverlapping(values, occurrence(event, event.startValue(), event.endValue(), evaluationZone), from, to);
            return List.copyOf(values.values());
        }

        for (TemporalValue start : recurringStarts(event, from, to, evaluationZone)) {
            if (excluded(event, start)) continue;
            RecurrenceOverride override = override(event, start);
            if (override != null) {
                if (!override.cancelled()) {
                    addIfOverlapping(values, occurrence(event, override.start(), override.end(), evaluationZone), from, to);
                }
                continue;
            }
            addIfOverlapping(values, occurrence(event, start, shiftedEnd(event, start), evaluationZone), from, to);
        }

        for (RecurrenceOverride override : event.overrides()) {
            if (!override.cancelled()) {
                addIfOverlapping(values, occurrence(event, override.start(), override.end(), evaluationZone), from, to);
            }
        }
        return values.values().stream()
                .sorted(Comparator.comparing(CalendarOccurrence::start))
                .limit(MAX_RESULTS)
                .toList();
    }

    private List<TemporalValue> recurringStarts(
            CalendarEvent event,
            Instant from,
            Instant to,
            ZoneId evaluationZone) {
        List<TemporalValue> starts = new ArrayList<>();
        starts.add(event.startValue());
        String rrule = event.recurrence().rrule();
        switch (event.startValue().kind()) {
            case DATE -> recurrenceEngine.dates(
                            rrule,
                            event.startValue().date(),
                            from.atZone(evaluationZone).toLocalDate(),
                            to.atZone(evaluationZone).toLocalDate().plusDays(1),
                            MAX_RESULTS)
                    .stream().map(TemporalValue::date).forEach(starts::add);
            case FLOATING -> recurrenceEngine.floating(
                            rrule,
                            event.startValue().localDateTime(),
                            LocalDateTime.ofInstant(from, evaluationZone),
                            LocalDateTime.ofInstant(to, evaluationZone),
                            MAX_RESULTS)
                    .stream().map(TemporalValue::floating).forEach(starts::add);
            case UTC -> recurrenceEngine.utc(rrule, event.startValue().instant(), from, to, MAX_RESULTS)
                    .stream().map(TemporalValue::utc).forEach(starts::add);
            case ZONED -> {
                ZoneId zone = event.startValue().zoneId();
                recurrenceEngine.zoned(
                                rrule,
                                event.startValue().localDateTime().atZone(zone),
                                from.atZone(zone),
                                to.atZone(zone),
                                MAX_RESULTS)
                        .stream()
                        .map(value -> TemporalValue.zoned(value.toLocalDateTime(), zone))
                        .forEach(starts::add);
            }
        }
        starts.addAll(event.recurrence().additionalDates());
        return starts;
    }

    private boolean excluded(CalendarEvent event, TemporalValue start) {
        return event.recurrence().excludedDates().stream().anyMatch(value -> sameTemporal(value, start));
    }

    private RecurrenceOverride override(CalendarEvent event, TemporalValue start) {
        return event.overrides().stream()
                .filter(value -> sameTemporal(value.recurrenceId(), start))
                .findFirst()
                .orElse(null);
    }

    private TemporalValue shiftedEnd(CalendarEvent event, TemporalValue start) {
        return switch (start.kind()) {
            case DATE -> TemporalValue.date(start.date().plusDays(
                    java.time.temporal.ChronoUnit.DAYS.between(event.startValue().date(), event.endValue().date())));
            case FLOATING -> TemporalValue.floating(start.localDateTime().plus(Duration.between(
                    event.startValue().localDateTime(), event.endValue().localDateTime())));
            case UTC -> TemporalValue.utc(start.instant().plus(Duration.between(
                    event.startValue().instant(), event.endValue().instant())));
            case ZONED -> {
                Duration duration = Duration.between(event.startValue().localDateTime(), event.endValue().localDateTime());
                yield TemporalValue.zoned(start.localDateTime().plus(duration), start.zoneId());
            }
        };
    }

    private CalendarOccurrence occurrence(
            CalendarEvent event,
            TemporalValue start,
            TemporalValue end,
            ZoneId evaluationZone) {
        return new CalendarOccurrence(event.id(), zoned(start, evaluationZone), zoned(end, evaluationZone));
    }

    private ZonedDateTime zoned(TemporalValue value, ZoneId evaluationZone) {
        return switch (value.kind()) {
            case DATE -> value.date().atStartOfDay(evaluationZone);
            case FLOATING -> value.localDateTime().atZone(evaluationZone);
            case UTC -> value.instant().atZone(ZoneOffset.UTC);
            case ZONED -> value.localDateTime().atZone(value.zoneId());
        };
    }

    private void addIfOverlapping(
            Map<String, CalendarOccurrence> values,
            CalendarOccurrence occurrence,
            Instant from,
            Instant to) {
        if (occurrence.end().toInstant().isAfter(from) && occurrence.start().toInstant().isBefore(to)) {
            values.put(occurrence.start().toInstant() + "|" + occurrence.end().toInstant(), occurrence);
        }
    }

    private boolean sameTemporal(TemporalValue left, TemporalValue right) {
        if (left.kind() != right.kind()) return false;
        return switch (left.kind()) {
            case DATE -> left.date().equals(right.date());
            case FLOATING -> left.localDateTime().equals(right.localDateTime());
            case UTC -> left.instant().equals(right.instant());
            case ZONED -> left.localDateTime().equals(right.localDateTime()) && left.zoneId().equals(right.zoneId());
        };
    }
}
