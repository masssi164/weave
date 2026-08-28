package com.massimotter.weave.backend.runner.adapter;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

final class RunnerPersistenceTime {

    private RunnerPersistenceTime() {}

    static OffsetDateTime utc(Instant value) {
        return value == null
                ? null
                : value.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    static Instant instant(Instant value) {
        return value == null ? null : value.truncatedTo(ChronoUnit.MICROS);
    }
}
