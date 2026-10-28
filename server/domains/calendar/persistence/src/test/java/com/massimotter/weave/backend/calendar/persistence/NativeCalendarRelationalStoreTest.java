package com.massimotter.weave.backend.calendar.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class NativeCalendarRelationalStoreTest {
    @Test
    void missingNormalizedPayloadRetainsTheFallbackAndUsesTheExactScope() {
        EntityManager entities = mock(EntityManager.class);
        NativeCalendarRelationalStore store = new NativeCalendarRelationalStore(entities);
        CalendarEvent fallback = new CalendarEvent(new CalendarId("calendar"), new EventId("event"),
                CalendarScope.workspace(), "Title", null,
                TemporalValue.utc(Instant.parse("2026-08-11T16:00:00Z")),
                TemporalValue.utc(Instant.parse("2026-08-11T17:00:00Z")),
                null, List.of(), null, List.of(), EventVersion.unknown(), null);

        assertThat(store.enrich(fallback, "workspace:one")).isSameAs(fallback);
        verify(entities).find(CalendarTemporalJpaEntity.class, new CalendarEventId("calendar", "workspace:one", "event"));
    }
}
