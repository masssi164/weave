package com.massimotter.weave.backend.portability;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RetryAfterParserTest {

    private static final Instant NOW = Instant.parse("2026-07-12T08:00:00Z");

    @Test
    void parsesDelaySecondsAndHttpDates() {
        assertThat(RetryAfterParser.parse("120", NOW)).isEqualTo(Duration.ofSeconds(120));
        assertThat(RetryAfterParser.parse("Sun, 12 Jul 2026 08:03:00 GMT", NOW))
                .isEqualTo(Duration.ofMinutes(3));
    }

    @Test
    void rejectsInvalidOrNegativeValuesWithoutThrowing() {
        assertThat(RetryAfterParser.parse("-1", NOW)).isNull();
        assertThat(RetryAfterParser.parse("provider-specific-value", NOW)).isNull();
        assertThat(RetryAfterParser.parse(null, NOW)).isNull();
    }
}
