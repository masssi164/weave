package com.massimotter.weave.backend.calendar.api.dav;

import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import java.time.OffsetDateTime;

public record CalDavEventResource(
        String eventId,
        CalendarScopeResponse scope,
        String etag,
        String calendarData,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt) {
}
