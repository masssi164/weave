package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.OffsetDateTime;

@Schema(description = "Safe Weave calendar storage reference. Raw provider paths, URLs, and credentials are deliberately not exposed.")
public record CalendarProviderRefResponse(
        @Schema(description = "Weave calendar projection family behind the facade.", example = "weave-calendar-facade")
        String provider,
        @Schema(description = "Weave projection object kind.", example = "calendar-event")
        String objectKind,
        @Schema(description = "Opaque Weave facade identifier for the storage object.")
        String opaqueId,
        @Schema(description = "Opaque revision token when available.")
        String etag,
        @Schema(description = "Last known storage synchronization/update timestamp when available.")
        OffsetDateTime lastSyncedAt,
        @Schema(description = "Always false for public Calendar API responses; raw storage paths must not be exposed.", example = "false")
        boolean rawProviderPathExposed) {

    public CalendarProviderRefResponse {
        provider = provider == null || provider.isBlank() ? "weave-calendar-facade" : provider.trim();
        objectKind = objectKind == null || objectKind.isBlank() ? "calendar-event" : objectKind.trim();
        opaqueId = opaqueId == null || opaqueId.isBlank() ? null : opaqueId.trim();
        etag = etag == null || etag.isBlank() ? null : etag.trim();
        rawProviderPathExposed = false;
    }

    public static CalendarProviderRefResponse caldavEvent(String opaqueId, String etag, OffsetDateTime lastSyncedAt) {
        return new CalendarProviderRefResponse("weave-calendar-facade", "calendar-event", opaqueId, etag, lastSyncedAt, false);
    }
}
