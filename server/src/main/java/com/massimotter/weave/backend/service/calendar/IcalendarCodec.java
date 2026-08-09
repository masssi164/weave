package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;

/**
 * Standards boundary for RFC 5545/iCalendar syntax and recurrence metadata.
 *
 * <p>iCal4j types stay inside the adapter implementation. Canonical Calendar
 * code sees only Weave domain and java.time values.</p>
 */
public interface IcalendarCodec {

    CalendarEvent decode(
            CalendarId calendarId,
            CalendarScope scope,
            EventVersion version,
            String calendarData);

    String encode(CalendarEvent event);

    default String normalize(
            CalendarId calendarId,
            CalendarScope scope,
            EventVersion version,
            String calendarData) {
        return encode(decode(calendarId, scope, version, calendarData));
    }
}
