package com.massimotter.weave.backend.model.calendar;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.RecurrenceFrequency;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

/** HTTP values only; provider identifiers and ORM/domain objects are never transport fields. */
public final class CalendarUserModels {
    private CalendarUserModels() {}

    public interface ClosedInput {
        @JsonAnySetter
        default void rejectUnknown(String name, Object ignored) {
            throw new IllegalArgumentException("Unsupported Calendar field");
        }
    }

    @Schema(name = "CalendarTimeValue", additionalProperties = Schema.AdditionalPropertiesValue.FALSE, description = "Exactly one value: DATE=date, FLOATING=localDateTime, UTC=instant, ZONED=localDateTime+timeZone. DATE end is exclusive; local values never contain an offset.")
    public record TimeValue(
            @NotNull TemporalKind kind,
            @Schema(type = "string", format = "date", nullable = true) String date,
            @Schema(type = "string", nullable = true, pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}$") String localDateTime,
            @Schema(type = "string", format = "date-time", nullable = true, pattern = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.0{1,9})?Z$", description = "Second-precision UTC instant ending in Z; an optional all-zero fractional suffix is accepted and normalized.") String instant,
            @Schema(nullable = true, description = "IANA timezone; present only for ZONED.") String timeZone) implements ClosedInput {}

    @Schema(name = "CalendarEventRecurrence", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Recurrence(
            @NotNull RecurrenceFrequency frequency,
            @Min(1) @Max(1000) @Schema(requiredMode = Schema.RequiredMode.REQUIRED) int interval,
            @Min(1) @Max(100000) @Schema(nullable = true) Integer count,
            @Valid @Schema(nullable = true, description = "Exclusive with count. DATE/FLOATING match start kind; UTC and ZONED use a UTC instant.") TimeValue until,
            @NotNull @Size(max = 1000) List<@NotNull @Valid TimeValue> additionalDates,
            @NotNull @Size(max = 1000) List<@NotNull @Valid TimeValue> excludedDates,
            @NotNull @Size(max = 366) List<@NotNull @Pattern(regexp = "[+-]?[0-9]{0,2}(MO|TU|WE|TH|FR|SA|SU)") String> byDay,
            @NotNull @Size(max = 62) List<@NotNull @Min(-31) @Max(31) Integer> byMonthDay,
            @NotNull @Size(max = 12) List<@NotNull @Min(1) @Max(12) Integer> byMonth,
            @NotNull @Size(max = 732) List<@NotNull @Min(-366) @Max(366) Integer> bySetPos,
            @Pattern(regexp = "MO|TU|WE|TH|FR|SA|SU") @Schema(nullable = true) String weekStart) implements ClosedInput {}

    @Schema(name = "CalendarEventOverride", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Override(
            @NotNull @Valid TimeValue recurrenceId,
            @Valid @Schema(nullable = true) TimeValue start,
            @Valid @Schema(nullable = true) TimeValue end,
            boolean cancelled,
            @Size(max = 512) @Schema(nullable = true) String title,
            @Size(max = 8192) @Schema(nullable = true) String description,
            @Size(max = 2048) @Schema(nullable = true) String location) implements ClosedInput {}

    @Schema(name = "CalendarEventAttendee", additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record Attendee(
            @Size(max = 512) @Schema(nullable = true) String displayName,
            @NotBlank @Size(max = 512) String address,
            @Pattern(regexp = "CHAIR|REQ-PARTICIPANT|OPT-PARTICIPANT|NON-PARTICIPANT") @Schema(nullable = true) String role,
            @Pattern(regexp = "NEEDS-ACTION|ACCEPTED|DECLINED|TENTATIVE|DELEGATED") @Schema(nullable = true) String response) implements ClosedInput {}

    @Schema(name = "CalendarEventWriteRequest", additionalProperties = Schema.AdditionalPropertiesValue.FALSE, description = "Complete supported event content. Unknown fields are rejected, never silently discarded. Identity, calendar, scope and thread references are server-owned.")
    public record WriteRequest(
            @NotBlank @Size(max = 512) String title,
            @Size(max = 8192) @Schema(nullable = true) String description,
            @NotNull @Valid TimeValue start,
            @NotNull @Valid TimeValue end,
            @Size(max = 2048) @Schema(nullable = true) String location,
            @NotNull @Size(max = 500) List<@NotNull @Valid Attendee> attendees,
            @Valid @Schema(nullable = true) Recurrence recurrence,
            @NotNull @Size(max = 1000) List<@NotNull @Valid Override> overrides) implements ClosedInput {}

    @Schema(name = "CalendarUserScope")
    public record Scope(
            @NotNull ScopeType type,
            @NotNull String spaceId,
            @Schema(nullable = true) String teamId,
            @Schema(nullable = true) String channelId) {}

    @Schema(name = "CalendarUserCalendar")
    public record Calendar(@NotNull String id, @NotNull Scope scope, @NotNull List<String> allowedActions) {}

    @Schema(name = "CalendarUserCalendars")
    public record Calendars(@NotNull List<Calendar> calendars) {}

    @Schema(name = "CalendarUserEvent")
    public record Event(
            @NotNull String id,
            @NotNull String calendarId,
            @NotNull Scope scope,
            @NotNull String meetingThreadRef,
            @NotNull String version,
            @NotNull List<String> allowedActions,
            @NotNull WriteRequest content) {}

    @Schema(name = "CalendarUserEventPreview", description = "Transient provider-backed event view. The opaque handle is short-lived and is not a stable resource identity or authorization grant.")
    public record EventPreview(
            @NotNull String handle,
            @NotNull String calendarId,
            @NotNull Scope scope,
            @NotNull Instant expiresAt,
            @NotNull List<String> allowedActions,
            @NotNull WriteRequest content) {}

    @Schema(name = "CalendarUserOccurrence", description = "An agenda-window projection. Event content retains its exact temporal intent.")
    public record Occurrence(@NotNull String eventId, @NotNull Instant startsAt, @NotNull Instant endsAt) {}

    @Schema(name = "CalendarUserPreviewOccurrence", description = "An agenda-window projection linked only to a transient preview handle.")
    public record PreviewOccurrence(@NotNull String previewHandle, @NotNull Instant startsAt, @NotNull Instant endsAt) {}

    @Schema(name = "CalendarUserAgenda")
    public record Agenda(@NotNull String calendarId, @NotNull Instant from, @NotNull Instant to,
            @NotNull String evaluationTimeZone, @NotNull List<Event> events,
            @NotNull List<Occurrence> occurrences, @NotNull List<EventPreview> previews,
            @NotNull List<PreviewOccurrence> previewOccurrences) {}
}
