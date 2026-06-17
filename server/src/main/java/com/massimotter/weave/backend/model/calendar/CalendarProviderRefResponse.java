package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Safe provider reference for a calendar event. Raw provider paths, URLs, and credentials are deliberately not exposed.")
public record CalendarProviderRefResponse(
        @Schema(description = "Canonical domain behind the facade.", example = "calendar")
        String domain,
        @Schema(description = "Provider object kind.", example = "calendar-event")
        String objectKind,
        @Schema(description = "Opaque Weave facade identifier for the provider object.")
        String opaqueId,
        @Schema(description = "Opaque revision token when available.")
        String etag,
        @Schema(description = "Last known provider synchronization/update timestamp when available.")
        OffsetDateTime lastSyncedAt,
        @Schema(description = "Always false for public Calendar API responses; raw provider paths must not be exposed.", example = "false")
        boolean rawProviderPathExposed) {

    public CalendarProviderRefResponse {
        domain = "calendar";
        objectKind = objectKind == null || objectKind.isBlank() ? "calendar-event" : objectKind.trim();
        opaqueId = opaqueId == null || opaqueId.isBlank() ? null : opaqueId.trim();
        etag = etag == null || etag.isBlank() ? null : etag.trim();
        rawProviderPathExposed = false;
    }

    public static CalendarProviderRefResponse caldavEvent(String opaqueId, String etag, OffsetDateTime lastSyncedAt) {
        return new CalendarProviderRefResponse("calendar", "event", opaqueId == null ? null : "provider-mapping://calendar/event/" + Integer.toHexString(opaqueId.hashCode()), etag, lastSyncedAt, false);
    }
}
