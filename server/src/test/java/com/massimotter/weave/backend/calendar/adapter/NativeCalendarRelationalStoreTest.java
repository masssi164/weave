package com.massimotter.weave.backend.calendar.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class NativeCalendarRelationalStoreTest {

    @Test
    void convertsCanonicalInstantsThroughPostgresSupportedOffsetDateTimes() {
        Instant canonical = Instant.parse("2026-08-11T16:26:41.159Z");

        OffsetDateTime jdbcValue = NativeCalendarRelationalStore.offset(canonical);

        assertThat(jdbcValue).isEqualTo(OffsetDateTime.parse("2026-08-11T16:26:41.159Z"));
        assertThat(NativeCalendarRelationalStore.instant(jdbcValue)).isEqualTo(canonical);
        assertThat(NativeCalendarRelationalStore.instant(
                OffsetDateTime.parse("2026-08-11T18:26:41.159+02:00")))
                .isEqualTo(canonical);
    }

    @Test
    void preservesNullableTemporalColumns() {
        assertThat(NativeCalendarRelationalStore.offset(null)).isNull();
        assertThat(NativeCalendarRelationalStore.instant(null)).isNull();
    }
}
