package com.massimotter.weave.backend.service.calendar;

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
        requireRange(seed, from, to);
        return expand(rrule, seed, from, to, maximumResults);
    }

    @Override
    public List<LocalDateTime> floating(
            String rrule,
            LocalDateTime seed,
            LocalDateTime from,
            LocalDateTime to,
            int maximumResults) {
        requireRange(seed, from, to);
        return expand(rrule, seed, from, to, maximumResults);
    }

    @Override
    public List<ZonedDateTime> zoned(
            String rrule,
            ZonedDateTime seed,
            ZonedDateTime from,
            ZonedDateTime to,
            int maximumResults) {
        requireRange(seed, from, to);
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
            if (exception instanceof CalendarAdapterException adapterException) {
                throw adapterException;
            }
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

    private static <T extends Comparable<? super T>> void requireRange(T seed, T from, T to) {
        if (seed == null || from == null || to == null || from.compareTo(to) >= 0) {
            throw invalid("Recurrence expansion requires a valid bounded time window.", null);
        }
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
