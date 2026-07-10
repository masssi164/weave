package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import java.time.OffsetDateTime;

public record CalDavEventResource(
        String eventId,
        CalendarScopeResponse scope,
        String etag,
        String calendarData,
        OffsetDateTime startsAt,
        OffsetDateTime endsAt) {
}
