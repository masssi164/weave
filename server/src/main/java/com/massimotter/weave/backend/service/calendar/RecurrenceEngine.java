package com.massimotter.weave.backend.service.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;

/** Window-bounded recurrence calculation without exposing library model types. */
public interface RecurrenceEngine {

    List<LocalDate> dates(String rrule, LocalDate seed, LocalDate from, LocalDate to, int maximumResults);

    List<LocalDateTime> floating(
            String rrule,
            LocalDateTime seed,
            LocalDateTime from,
            LocalDateTime to,
            int maximumResults);

    List<Instant> utc(String rrule, Instant seed, Instant from, Instant to, int maximumResults);

    List<ZonedDateTime> zoned(
            String rrule,
            ZonedDateTime seed,
            ZonedDateTime from,
            ZonedDateTime to,
            int maximumResults);
}
