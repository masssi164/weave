package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;

@Schema(description = "Patch a calendar event through the product calendar facade.")
public record UpdateCalendarEventRequest(
        @Schema(description = "Updated event title.", example = "Planning")
        @Size(max = 255)
        String title,
        @Schema(description = "Updated plain-text event description.")
        @Size(max = 8192)
        String description,
        @Schema(description = "Updated event start timestamp.")
        OffsetDateTime startsAt,
        @Schema(description = "Updated event end timestamp.")
        OffsetDateTime endsAt,
        @Schema(description = "Updated IANA timezone used for display and editing.", example = "Europe/Berlin")
        @Size(max = 128)
        String timezone,
        @Schema(description = "Updated event location.")
        @Size(max = 1024)
        String location,
        @Schema(description = "Updated all-day flag.")
        Boolean allDay,
        @Schema(description = "Opaque revision token supplied by the client for conflict detection.")
        String etag,
        @Schema(description = "Calendar scope for the updated event. Defaults to the event id scope or workspace calendar.")
        CalendarScopeResponse scope) {

    public UpdateCalendarEventRequest(
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            String location,
            Boolean allDay,
            String etag) {
        this(title, description, startsAt, endsAt, timezone, location, allDay, etag, null);
    }

    @AssertTrue(message = "endsAt must be after startsAt")
    public boolean isTimeRangeValid() {
        if (startsAt == null || endsAt == null) {
            return true;
        }
        return endsAt.isAfter(startsAt);
    }
}
