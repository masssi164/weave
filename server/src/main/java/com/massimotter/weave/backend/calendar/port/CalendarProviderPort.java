package com.massimotter.weave.backend.calendar.port;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.portability.ProviderCapabilityProbeResult;
import com.massimotter.weave.backend.portability.ProviderReadiness;
import java.time.Instant;
import java.util.List;

public interface CalendarProviderPort {

    boolean configured();

    ProviderReadiness readiness();

    default ProviderCapabilityProbeResult healthProbe() {
        ProviderReadiness readiness = readiness();
        return readiness.available()
                ? ProviderCapabilityProbeResult.available(readiness.supportSafeCode())
                : ProviderCapabilityProbeResult.degraded(readiness.supportSafeCode());
    }

    ProviderConformanceProfile conformanceProfile();

    /**
     * True only when the adapter's sync token is itself a stable, Weave-owned northbound token.
     * External adapters remain wrapped by the facade so provider tokens never cross the boundary.
     */
    default boolean ownsNorthboundSyncTokens() {
        return false;
    }

    List<CalendarEvent> query(CalendarId calendarId, CalendarScope scope, Instant from, Instant to);

    CalendarEvent read(CalendarId calendarId, CalendarScope scope, EventId id);

    CalendarEvent write(CalendarWrite write);

    void delete(CalendarId calendarId, CalendarScope scope, EventId id, EventVersion expectedVersion);

    List<FreeBusyWindow> freeBusy(CalendarId calendarId, CalendarScope scope, Instant from, Instant to);

    CalendarChangeSet changes(CalendarId calendarId, CalendarScope scope, String sinceToken);
}
