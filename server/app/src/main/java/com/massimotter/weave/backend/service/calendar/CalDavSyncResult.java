package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import java.util.List;

public record CalDavSyncResult(
        String syncToken,
        CalendarScopeResponse scope,
        List<CalDavEventResource> changedResources,
        List<String> deletedEventIds) {

    public CalDavSyncResult {
        changedResources = changedResources == null ? List.of() : List.copyOf(changedResources);
        deletedEventIds = deletedEventIds == null ? List.of() : List.copyOf(deletedEventIds);
    }
}
