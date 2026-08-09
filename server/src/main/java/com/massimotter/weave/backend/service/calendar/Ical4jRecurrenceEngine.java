package com.massimotter.weave.backend.service.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.Temporal;
import java.util.List;
import java.util.Map;
import net.fortuna.ical4j.model.Recur;

/** iCal4j-backed, request-window-bounded recurrence calculation. */
public final class Ical4jRecurrenceEngine implements RecurrenceEngine {

    static final int MAX_RESULTS = 10_000;

    @Override
    public List<LocalDate> dates(
            String rrule,
            LocalDate seed,
            LocalDate from,
            LocalDate to,
            int maximumResults) {
        if (seed == null || from == null || to == null || !from.isBefore(to)) {
            throw invalid("Recurrence expansion requires a valid bounded date window.", null);
        }
        return expand(rrule, seed, from, to, maximumResults);
    }

    @Override
    public List<LocalDateTime> floating(
            String rrule,
            LocalDateTime seed,
            LocalDateTime from,
            LocalDateTime to,
            int maximumResults) {
        if (seed == null || from == null || to == null || !from.isBefore(to)) {
            throw invalid("Recurrence expansion requires a valid bounded floating-time window.", null);
        }
        return expand(rrule, seed, from, to, maximumResults);
    }

    @Override
    public List<Instant> utc(
            String rrule,
            Instant seed,
            Instant from,
            Instant to,
            int maximumResults) {
        if (seed == null || from == null || to == null || !from.isBefore(to)) {
            throw invalid("Recurrence expansion requires a valid bounded UTC window.", null);
        }
        return expand(rrule, seed, from, to, maximumResults);
    }

    @Override
    public List<ZonedDateTime> zoned(
            String rrule,
            ZonedDateTime seed,
            ZonedDateTime from,
            ZonedDateTime to,
            int maximumResults) {
        if (seed == null || from == null || to == null || !from.isBefore(to)) {
            throw invalid("Recurrence expansion requires a valid bounded zoned-time window.", null);
        }
        if (!seed.getZone().equals(from.getZone()) || !seed.getZone().equals(to.getZone())) {
            throw invalid("Zoned recurrence windows must retain the master event TZID.", null);
        }
        return expand(rrule, seed, from, to, maximumResults);
    }

    private static <T extends Temporal> List<T> expand(
            String rrule,
            T seed,
            T from,
            T to,
            int maximumResults) {
        String rule = normalizeRule(rrule);
        int limit = requireLimit(maximumResults);
        try {
            Recur<T> recurrence = new Recur<>(rule, false);
            List<T> result = recurrence.getDates(seed, from, to, limit);
            if (result.size() > limit) {
                throw invalid("Recurrence expansion exceeds the requested result limit.", null);
            }
            return List.copyOf(result);
        } catch (IllegalArgumentException exception) {
            throw invalid("RRULE is invalid or outside the supported recurrence grammar.", exception);
        }
    }

    private static String normalizeRule(String value) {
        if (value == null || value.isBlank()) {
            throw invalid("RRULE must not be blank.", null);
        }
        String normalized = value.trim();
        if (normalized.regionMatches(true, 0, "RRULE:", 0, 6)) {
            normalized = normalized.substring(6);
        }
        return normalized;
    }

    private static int requireLimit(int value) {
        if (value < 1 || value > MAX_RESULTS) {
            throw invalid("Recurrence result limit is outside the supported range.", null);
        }
        return value;
    }

    private static CalendarAdapterException invalid(String message, Throwable cause) {
        return new CalendarAdapterException(
                CalendarAdapterException.Type.INVALID_REQUEST,
                message,
                Map.of(
                        "module", "calendar",
                        "operation", "recurrence-engine",
                        "errorCode", "calendar-recurrence-invalid",
                        "supportSafe", true),
                cause);
    }
}
