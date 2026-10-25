package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Safe attendee metadata for a calendar event. Does not expose provider credentials or raw scheduling tokens.")
public record CalendarAttendeeResponse(
        @Schema(description = "Display name when supplied by the calendar data.", example = "Ada Lovelace")
        String name,
        @Schema(description = "Email address when supplied by the calendar data.", example = "ada@example.com")
        String email,
        @Schema(description = "iCalendar attendee role normalized to lower-case when available.", example = "req-participant")
        String role,
        @Schema(description = "iCalendar participation status normalized to lower-case when available.", example = "accepted")
        String responseStatus) {

    public CalendarAttendeeResponse {
        name = blankToNull(name);
        email = blankToNull(email);
        role = normalize(role);
        responseStatus = normalize(responseStatus);
    }

    private static String normalize(String value) {
        String normalized = blankToNull(value);
        return normalized == null ? null : normalized.toLowerCase(java.util.Locale.ROOT);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
