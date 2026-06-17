package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Opaque, support-safe provenance reference for a calendar event. Backing system names, raw paths, URLs, and credentials are deliberately not exposed.")
public record CalendarProvenanceRefResponse(
        @Schema(description = "Canonical object kind behind the Calendar facade.", example = "calendar-meeting")
        String objectKind,
        @Schema(description = "Opaque Weave facade provenance identifier for the calendar object.")
        String provenanceRef,
        @Schema(description = "Opaque revision token when available.")
        String etag,
        @Schema(description = "Last known facade synchronization/update timestamp when available.")
        OffsetDateTime lastSyncedAt,
        @Schema(description = "Always false for public Calendar API responses; raw backing paths must not be exposed.", example = "false")
        boolean rawBackingPathExposed) {

    public CalendarProvenanceRefResponse {
        objectKind = objectKind == null || objectKind.isBlank() ? "calendar-meeting" : objectKind.trim();
        provenanceRef = provenanceRef == null || provenanceRef.isBlank() ? null : provenanceRef.trim();
        etag = etag == null || etag.isBlank() ? null : etag.trim();
        rawBackingPathExposed = false;
    }

    public static CalendarProvenanceRefResponse calendarMeeting(String opaqueId, String etag, OffsetDateTime lastSyncedAt) {
        return new CalendarProvenanceRefResponse("calendar-meeting", provenanceRef(opaqueId), etag, lastSyncedAt, false);
    }

    private static String provenanceRef(String opaqueId) {
        if (opaqueId == null || opaqueId.isBlank()) {
            return null;
        }
        return "provenance://calendar-meetings/" + Integer.toHexString(opaqueId.hashCode());
    }
}
