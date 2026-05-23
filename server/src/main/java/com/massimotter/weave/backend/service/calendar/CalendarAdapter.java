package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import java.time.OffsetDateTime;
import java.util.List;

public interface CalendarAdapter {

    List<CalendarEventResponse> list(CalendarPrincipal principal, OffsetDateTime from, OffsetDateTime to)
            throws CalendarAdapterException;

    default List<CalendarEventResponse> list(
            CalendarPrincipal principal,
            CalendarScopeResponse scope,
            OffsetDateTime from,
            OffsetDateTime to) throws CalendarAdapterException {
        return list(principal, from, to);
    }

    CalendarEventResponse create(CalendarPrincipal principal, CreateCalendarEventRequest request)
            throws CalendarAdapterException;

    CalendarEventResponse read(CalendarPrincipal principal, String id) throws CalendarAdapterException;

    default CalendarEventResponse read(CalendarPrincipal principal, CalendarScopeResponse scope, String id)
            throws CalendarAdapterException {
        return read(principal, id);
    }

    CalendarEventResponse update(CalendarPrincipal principal, String id, UpdateCalendarEventRequest request)
            throws CalendarAdapterException;

    default CalendarEventResponse update(
            CalendarPrincipal principal,
            CalendarScopeResponse scope,
            String id,
            UpdateCalendarEventRequest request) throws CalendarAdapterException {
        return update(principal, id, request);
    }

    void delete(CalendarPrincipal principal, String id) throws CalendarAdapterException;

    default void delete(CalendarPrincipal principal, CalendarScopeResponse scope, String id) throws CalendarAdapterException {
        delete(principal, id);
    }
}
