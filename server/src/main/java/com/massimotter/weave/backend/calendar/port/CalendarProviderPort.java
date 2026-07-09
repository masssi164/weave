package com.massimotter.weave.backend.calendar.port;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import java.time.Instant;
import java.util.List;

public interface CalendarProviderPort {

    boolean configured();

    ProviderConformanceProfile conformanceProfile();

    List<CalendarEvent> query(CalendarScope scope, Instant from, Instant to);

    CalendarEvent read(CalendarScope scope, EventId id);

    CalendarEvent write(CalendarEvent event, EventVersion expectedVersion);

    void delete(CalendarScope scope, EventId id, EventVersion expectedVersion);

    List<FreeBusyWindow> freeBusy(CalendarScope scope, Instant from, Instant to);

    List<CalendarChange> changes(CalendarScope scope, String sinceToken);
}
