package com.massimotter.weave.backend.portability;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

public final class RetryAfterParser {

    private RetryAfterParser() {
    }

    public static Duration parse(String value, Instant now) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        try {
            long seconds = Long.parseLong(normalized);
            return seconds < 0 ? null : Duration.ofSeconds(seconds);
        } catch (NumberFormatException ignored) {
            // Retry-After also permits an RFC 1123 HTTP date.
        }
        try {
            Instant retryAt = ZonedDateTime.parse(normalized, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            Duration delay = Duration.between(now, retryAt);
            return delay.isNegative() ? Duration.ZERO : delay;
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
