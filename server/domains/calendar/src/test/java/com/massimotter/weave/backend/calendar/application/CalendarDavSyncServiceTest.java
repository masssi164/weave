package com.massimotter.weave.backend.calendar.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import com.massimotter.weave.backend.calendar.domain.CalendarAdapterException;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.TemporalValue;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.calendar.provider.icalendar.IcalendarMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalendarDavSyncServiceTest {
    private static final CalendarId CALENDAR = new CalendarId("tenant-calendar");
    private static final CalendarScope SCOPE = CalendarScope.workspace();
    private static final CalendarScopeResponse WIRE_SCOPE = CalendarScopeResponse.workspace();
    private final CalendarProviderPort provider = mock(CalendarProviderPort.class);
    private final IcalendarMapper mapper = mock(IcalendarMapper.class);
    private final CalendarDavSyncService service = new CalendarDavSyncService(provider, mapper);

    @Test
    void serializationFailurePrecedesTokenIssuanceAndPreservesOriginalFailure() {
        var event = changedEvent();
        var failure = new CalendarAdapterException(CalendarAdapterException.Type.INVALID_RESPONSE,
                "Fixture serialization failure", Map.of("supportSafe", true));
        when(mapper.toNorthboundIcalendar(event, WIRE_SCOPE)).thenThrow(failure);

        assertThatThrownBy(() -> service.synchronize(CALENDAR, WIRE_SCOPE, SCOPE, null)).isSameAs(failure);

        verify(provider, never()).ownsNorthboundSyncTokens();
    }

    @Test
    void wrappingFollowsSuccessfulSerializationAndRejectsCrossCalendarOrScopeReuse() {
        var event = changedEvent();
        when(mapper.toNorthboundIcalendar(event, WIRE_SCOPE)).thenReturn("fixture-ics");
        var result = service.synchronize(CALENDAR, WIRE_SCOPE, SCOPE, null);

        assertThat(result.token()).startsWith("weave-caldav-sync-").doesNotContain("remote-token");
        assertThat(result.changed()).singleElement().satisfies(resource -> assertThat(resource.calendarData()).isEqualTo("fixture-ics"));
        var order = inOrder(provider, mapper);
        order.verify(provider).changes(CALENDAR, SCOPE, null);
        order.verify(provider).read(CALENDAR, SCOPE, event.id());
        order.verify(mapper).toNorthboundIcalendar(event, WIRE_SCOPE);
        order.verify(provider).ownsNorthboundSyncTokens();
        assertThatThrownBy(() -> service.synchronize(new CalendarId("other-calendar"), WIRE_SCOPE, SCOPE, result.token()))
                .isInstanceOf(ApiErrorException.class).hasMessageContaining("sync token");
        assertThatThrownBy(() -> service.synchronize(CALENDAR, CalendarScopeResponse.team("other", "Other"), SCOPE, result.token()))
                .isInstanceOf(ApiErrorException.class).hasMessageContaining("sync token");
        verify(provider, times(1)).changes(any(), any(), any());
    }

    private CalendarEvent changedEvent() {
        var event = new CalendarEvent(CALENDAR, new EventId("event"), SCOPE, "Event", null,
                TemporalValue.utc(Instant.parse("2026-01-01T09:00:00Z")),
                TemporalValue.utc(Instant.parse("2026-01-01T10:00:00Z")), null,
                List.of(), null, List.of(), new EventVersion("etag"), null);
        when(provider.changes(CALENDAR, SCOPE, null)).thenReturn(new CalendarChangeSet("remote-token",
                List.of(new CalendarChange("remote-token", event.id(), false, event.version()))));
        when(provider.read(CALENDAR, SCOPE, event.id())).thenReturn(event);
        return event;
    }
}
