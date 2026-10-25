package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Calendar facade event listing response.")
public record CalendarEventsResponse(
        @Schema(description = "Calendar scope used for this facade response.")
        CalendarScopeResponse scope,
        List<CalendarEventResponse> events) {

    public CalendarEventsResponse(List<CalendarEventResponse> events) {
        this(CalendarScopeResponse.workspace(), events);
    }

    public CalendarEventsResponse {
        scope = scope == null ? CalendarScopeResponse.workspace() : scope;
    }
}
