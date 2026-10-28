package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.calendar.api.dav.CalDavEventResource;
import com.massimotter.weave.backend.calendar.provider.icalendar.IcalendarMapper;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import com.massimotter.weave.backend.calendar.domain.CalendarAdapterException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpStatus;

/** Provider change synchronization and northbound scope-bound token wrapping. */
public final class CalendarDavSyncService {
    private final CalendarProviderPort provider;
    private final IcalendarMapper icalendarMapper;
    private final Map<String, CalendarSyncCursor> syncCursors = new ConcurrentHashMap<>();

    public CalendarDavSyncService(CalendarProviderPort provider, IcalendarMapper icalendarMapper) {
        this.provider = Objects.requireNonNull(provider);
        this.icalendarMapper = Objects.requireNonNull(icalendarMapper);
    }

    SyncBatch synchronize(
            CalendarId calendarId, CalendarScopeResponse scope, CalendarScope domainScope, String weaveSyncToken) {
        String providerToken = providerSyncToken(weaveSyncToken, calendarId, scope, provider);
        try {
            var changes = provider.changes(calendarId, domainScope, providerToken);
            List<CalDavEventResource> changed = changes.changes().stream()
                    .filter(change -> !change.deleted())
                    .map(change -> provider.read(calendarId, domainScope, change.eventId()))
                    .map(event -> resource(event, scope)).toList();
            List<String> deleted = changes.changes().stream()
                    .filter(CalendarChange::deleted)
                    .map(change -> change.eventId().value()).toList();
            String token = changes.syncToken();
            if (!provider.ownsNorthboundSyncTokens()) {
                token = "weave-caldav-sync-" + UUID.randomUUID();
                syncCursors.put(token, new CalendarSyncCursor(calendarId, scope.id(), changes.syncToken()));
            }
            return new SyncBatch(token, changed, deleted);
        } catch (CalendarAdapterException exception) {
            if (exception.type() == CalendarAdapterException.Type.CONFLICT) throw invalidSyncToken();
            throw exception;
        }
    }

    CalDavEventResource resource(CalendarEvent event, CalendarScopeResponse scope) {
        return new CalDavEventResource(event.id().value(), scope, event.version().value(),
                icalendarMapper.toNorthboundIcalendar(event, scope),
                event.startsAt().toOffsetDateTime(), event.endsAt().toOffsetDateTime());
    }

    record SyncBatch(String token, List<CalDavEventResource> changed, List<String> deleted) {}
    private record CalendarSyncCursor(CalendarId calendarId, String scopeId, String providerToken) {}

    private String providerSyncToken(
            String weaveSyncToken,
            CalendarId calendarId,
            CalendarScopeResponse scope,
            CalendarProviderPort provider) {
        if (weaveSyncToken == null || weaveSyncToken.isBlank()) {
            return null;
        }
        if (provider.ownsNorthboundSyncTokens()) {
            return weaveSyncToken.trim();
        }
        CalendarSyncCursor cursor = syncCursors.get(weaveSyncToken.trim());
        if (cursor == null
                || !cursor.calendarId().equals(calendarId)
                || !cursor.scopeId().equals(scope.id())) {
            throw invalidSyncToken();
        }
        return cursor.providerToken();
    }

    private ApiErrorException invalidSyncToken() {
        return new ApiErrorException(
                HttpStatus.CONFLICT,
                "caldav-sync-token-invalid",
                "The CalDAV sync token is invalid or no longer belongs to this calendar scope.",
                Map.of(
                        "module", "calendar",
                        "operation", "caldav-sync-collection",
                        "supportSafe", true));
    }

}
