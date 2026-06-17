package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;
import java.util.List;

@Schema(description = "Calendar event metadata returned by the Weave calendar facade.")
public record CalendarEventResponse(
        @Schema(description = "Stable backend facade identifier for this event.", example = "calendar:personal:123")
        String id,
        @Schema(description = "Event title.", example = "Planning")
        String title,
        @Schema(description = "Plain-text event description when available.")
        String description,
        @Schema(description = "Event start timestamp.")
        OffsetDateTime startsAt,
        @Schema(description = "Event end timestamp.")
        OffsetDateTime endsAt,
        @Schema(description = "IANA timezone used for display and editing.", example = "Europe/Berlin")
        String timezone,
        @Schema(description = "Event location when provided.", example = "Office")
        String location,
        @Schema(description = "Whether the event is all-day.")
        boolean allDay,
        @Schema(description = "Opaque revision token used for conflict detection when available.")
        String etag,
        @Schema(description = "Calendar scope used for this facade event.")
        CalendarScopeResponse scope,
        @Schema(description = "Safe event thread/context metadata. Does not expose encrypted Matrix message contents.")
        CalendarThreadRefResponse threadRef,
        @Schema(description = "Safe attendee metadata when supplied by the backing calendar data.")
        List<CalendarAttendeeResponse> attendees,
        @Schema(description = "Opaque support-safe provenance reference. Does not expose provider names, raw paths, URLs, or credentials.")
        CalendarProvenanceRefResponse provenanceRef,
        @Schema(description = "Last known event update timestamp when available.")
        OffsetDateTime updatedAt) {

    public CalendarEventResponse(
            String id,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
                String timezone,
                String location,
                boolean allDay,
                String etag) {
        this(id, title, description, startsAt, endsAt, timezone, location, allDay, etag,
                CalendarScopeResponse.workspace(), null, List.of(), null, null);
    }

    public CalendarEventResponse(
            String id,
            String title,
            String description,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String timezone,
            String location,
                boolean allDay,
                String etag,
                CalendarScopeResponse scope) {
        this(id, title, description, startsAt, endsAt, timezone, location, allDay, etag, scope, null, List.of(), null, null);
    }

    public CalendarEventResponse {
        scope = scope == null ? CalendarScopeResponse.workspace() : scope;
        threadRef = threadRef == null ? CalendarThreadRefResponse.forEvent(scope, id) : threadRef;
        attendees = attendees == null ? List.of() : List.copyOf(attendees);
        provenanceRef = provenanceRef == null ? CalendarProvenanceRefResponse.calendarMeeting(id, etag, updatedAt) : provenanceRef;
    }
}
