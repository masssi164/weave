package com.massimotter.weave.backend.calendar.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class CalendarTemporalValueTest {
    @Test
    void dateAndFloatingValuesRequireAnExplicitEvaluationZone() {
        var date = TemporalValue.date(LocalDate.of(2026, 1, 2));
        var floating = TemporalValue.floating(LocalDateTime.of(2026, 1, 2, 9, 0));
        assertThrows(IllegalArgumentException.class, () -> date.toInstant(null));
        assertThrows(IllegalArgumentException.class, () -> floating.toInstant(null));
        assertEquals(Instant.parse("2026-01-02T08:00:00Z"),
                floating.toInstant(ZoneId.of("Europe/Berlin")));
    }

    @Test
    void utcValuesNeverAcquireTheMachineDefaultZone() {
        Instant instant = Instant.parse("2026-01-02T09:00:00Z");
        var value = TemporalValue.utc(instant);
        assertEquals(instant, value.toInstant(null));
        assertEquals(instant, value.toInstant(ZoneId.of("Pacific/Apia")));
    }

    @Test
    void zonedValuesUseTheirOwnZoneRatherThanTheEvaluationZone() {
        var value = TemporalValue.zoned(LocalDateTime.of(2026, 1, 2, 9, 0),
                ZoneId.of("Europe/Berlin"));
        assertEquals(Instant.parse("2026-01-02T08:00:00Z"),
                value.toInstant(ZoneId.of("Pacific/Apia")));
    }
}
