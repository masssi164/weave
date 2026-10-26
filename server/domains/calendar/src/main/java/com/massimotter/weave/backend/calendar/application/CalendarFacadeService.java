package com.massimotter.weave.backend.calendar.application;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.spaces.authorization.ContextPermission;
import com.massimotter.weave.backend.calendar.api.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.calendar.api.CalendarClientSetupResponse;
import com.massimotter.weave.backend.calendar.api.CalendarAttendeeResponse;
import com.massimotter.weave.backend.calendar.api.CalendarEventResponse;
import com.massimotter.weave.backend.calendar.api.CalendarEventsResponse;
import com.massimotter.weave.backend.calendar.api.CalendarNativeSyncSetupResponse;
import com.massimotter.weave.backend.calendar.api.CalendarScopeResponse;
import com.massimotter.weave.backend.calendar.api.CalendarScopesResponse;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.calendar.api.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.calendar.api.CreateCalendarEventRequest;
import com.massimotter.weave.backend.calendar.api.UpdateCalendarEventRequest;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.calendar.domain.CalendarAdapterException;
import com.massimotter.weave.backend.calendar.application.CalendarFacadeAuthorization.PrincipalContext;
import com.massimotter.weave.backend.calendar.api.dav.CalDavEventResource;
import com.massimotter.weave.backend.calendar.api.dav.CalDavSyncResult;
import com.massimotter.weave.backend.calendar.provider.icalendar.IcalendarMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CalendarFacadeService {

    private final CalendarProviderPort calendarProviderPort;
    private final CalendarFacadeAuthorization authorization;
    private final CalendarClientSetupService clientSetup;
    private final CalendarDavSyncService davSync;
    private final IcalendarMapper icalendarMapper;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    public CalendarFacadeService(
            CalendarProviderPort calendarProviderPort,
            CalendarFacadeAuthorization authorization,
            CalendarClientSetupService clientSetup,
            CalendarDavSyncService davSync,
            IcalendarMapper icalendarMapper,
            AuditEventPublisher auditEventPublisher,
            @Qualifier("calendarFacadeClock") Clock clock) {
        this.calendarProviderPort = Objects.requireNonNull(calendarProviderPort);
        this.authorization = Objects.requireNonNull(authorization);
        this.clientSetup = Objects.requireNonNull(clientSetup);
        this.davSync = Objects.requireNonNull(davSync);
        this.icalendarMapper = Objects.requireNonNull(icalendarMapper);
        this.auditEventPublisher = Objects.requireNonNull(auditEventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    public CalendarScopesResponse scopes() {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "list-scopes");
        return new CalendarScopesResponse(calendarScopes());
    }

    public CalendarEventsResponse list(OffsetDateTime from, OffsetDateTime to) {
        return list(from, to, null, null, null);
    }

    public CalendarEventsResponse list(
            OffsetDateTime from,
            OffsetDateTime to,
            String scopeType,
            String teamId,
            String channelId) {
        validateRange(from, to);
        CalendarScopeResponse scope = resolveScope(scopeType, teamId, channelId);
        return listCalDavEvents(scope, from, to);
    }

    public CalendarEventsResponse listCalDavEvents(
            CalendarScopeResponse scope,
            OffsetDateTime from,
            OffsetDateTime to) {
        List<CalDavEventResource> resources = listCalDavResources(scope, from, to);
        return new CalendarEventsResponse(
                normalizeScope(scope, "caldav-list-events"),
                resources.stream().map(this::eventResponse).toList());
    }

    public List<CalDavEventResource> listCalDavResources(
            CalendarScopeResponse scope,
            OffsetDateTime from,
            OffsetDateTime to) {
        validateRange(from, to);
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "caldav-list-events");
        authorization.requireContextPermission(normalizedScope, ContextPermission.VIEW, "list-events");
        try {
            return calendarProviderPort
                    .query(calendarId(), toDomainScope(normalizedScope), instant(from), instant(to)).stream()
                    .map(event -> davSync.resource(event, normalizedScope))
                    .toList();
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "list-events");
        }
    }

    public List<FreeBusyWindow> calDavFreeBusy(
            CalendarScopeResponse scope,
            OffsetDateTime from,
            OffsetDateTime to) {
        validateRange(from, to);
        if (from == null || to == null) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "caldav-time-range-required",
                    "CalDAV free-busy requires a bounded time range.",
                    Map.of("module", "calendar", "operation", "caldav-free-busy"));
        }
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "caldav-free-busy");
        authorization.requireContextPermission(normalizedScope, ContextPermission.VIEW, "free-busy");
        try {
            return calendarProviderPort.freeBusy(
                    calendarId(),
                    toDomainScope(normalizedScope),
                    from.toInstant(),
                    to.toInstant());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "free-busy");
        }
    }

    public CalDavSyncResult syncCalDavResources(CalendarScopeResponse scope, String weaveSyncToken) {
        CalendarScopeResponse normalized = normalizeScope(scope, "caldav-sync-collection");
        authorization.requireContextPermission(normalized, ContextPermission.VIEW, "sync-events");
        try {
            var changes = davSync.synchronize(calendarId(), normalized, toDomainScope(normalized), weaveSyncToken);
            return new CalDavSyncResult(changes.token(), normalized, changes.changed(), changes.deleted());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "sync-events");
        }
    }

    public CalendarEventResponse create(CreateCalendarEventRequest request) {
        CalendarScopeResponse scope = normalizeScope(request.scope(), "create-event");
        authorization.requireContextPermission(scope, ContextPermission.EDIT, "create-event");
        try {
            CalendarEvent event = eventFrom(request, scope, new EventId(UUID.randomUUID() + "@weave.test"));
            publishEventWriteAudit(scope, event.id(), "create-event", EventVersion.unknown());
            return toResponse(
                    calendarProviderPort.write(new CalendarWrite(event, WriteIntent.CREATE, EventVersion.unknown())),
                    scope,
                    true);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "create-event");
        }
    }

    public CalendarEventResponse read(String id) {
        ScopedEventId eventId = scopedEventId(id);
        authorization.requireContextPermission(eventId.scope(), ContextPermission.VIEW, "read-event");
        try {
            return toResponse(calendarProviderPort.read(
                    calendarId(),
                    toDomainScope(eventId.scope()),
                    new EventId(eventId.rawId())), eventId.scope(), true);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "read-event");
        }
    }

    public CalendarEventResponse update(String id, UpdateCalendarEventRequest request) {
        ScopedEventId eventId = scopedEventId(id);
        CalendarScopeResponse scope = request.scope() == null ? eventId.scope() : normalizeScope(request.scope(), "update-event");
        authorization.requireContextPermission(scope, ContextPermission.EDIT, "update-event");
        try {
            CalendarProviderPort adapter = calendarProviderPort;
            CalendarEvent existing = adapter.read(calendarId(), toDomainScope(scope), new EventId(eventId.rawId()));
            CalendarEvent updated = merge(existing, request, scope);
            EventVersion expected = request.etag() == null
                    ? EventVersion.unknown()
                    : new EventVersion(request.etag());
            publishEventWriteAudit(scope, updated.id(), "update-event", expected);
            return toResponse(
                    adapter.write(new CalendarWrite(updated, WriteIntent.UPDATE, expected)),
                    scope,
                    true);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "update-event");
        }
    }

    public void delete(String id) {
        ScopedEventId eventId = scopedEventId(id);
        authorization.requireContextPermission(eventId.scope(), ContextPermission.EDIT, "delete-event");
        try {
            publishEventWriteAudit(
                    eventId.scope(), new EventId(eventId.rawId()), "delete-event", EventVersion.unknown());
            calendarProviderPort.delete(
                    calendarId(),
                    toDomainScope(eventId.scope()),
                    new EventId(eventId.rawId()),
                    EventVersion.unknown());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "delete-event");
        }
    }

    public String readCalDavEventIcs(String eventUid) {
        return readCalDavEventIcs(eventUid, CalendarScopeResponse.workspace());
    }

    public String readCalDavEventIcs(String eventUid, CalendarScopeResponse scope) {
        return readCalDavResource(eventUid, scope).calendarData();
    }

    public CalendarEventResponse readCalDavEvent(String eventUid, CalendarScopeResponse scope) {
        return eventResponse(readCalDavResource(eventUid, scope));
    }

    public CalDavEventResource readCalDavResource(String eventUid, CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "read-caldav-event");
        authorization.requireContextPermission(normalizedScope, ContextPermission.VIEW, "read-event");
        try {
            return davSync.resource(calendarProviderPort.read(
                    calendarId(),
                    toDomainScope(normalizedScope),
                    new EventId(eventUid)), normalizedScope);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "read-event");
        }
    }

    public CalendarEventResponse putCalDavEventIcs(
            String eventUid,
            String calendarData,
            String ifMatch,
            String ifNoneMatch) {
        return putCalDavEventIcs(eventUid, calendarData, ifMatch, ifNoneMatch, CalendarScopeResponse.workspace());
    }

    public CalendarEventResponse putCalDavEventIcs(
            String eventUid,
            String calendarData,
            String ifMatch,
            String ifNoneMatch,
            CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "put-caldav-event");
        CalendarEvent parsed = parseCalDavEvent(eventUid, normalizedScope, calendarData, "put-caldav-event");
        if ("*".equals(ifNoneMatch)) {
            authorization.requireContextPermission(normalizedScope, ContextPermission.EDIT, "create-event");
            try {
                publishEventWriteAudit(
                        normalizedScope, parsed.id(), "create-event", EventVersion.unknown());
                return toResponse(calendarProviderPort.write(new CalendarWrite(
                        parsed,
                        WriteIntent.CREATE,
                        EventVersion.unknown())), normalizedScope, false);
            } catch (CalendarAdapterException exception) {
                throw apiError(exception, "create-event");
            }
        }
        authorization.requireContextPermission(normalizedScope, ContextPermission.EDIT, "update-event");
        try {
            CalendarProviderPort provider = calendarProviderPort;
            EventVersion expectedVersion = EventVersion.unknown();
            if (ifMatch != null && !ifMatch.isBlank()) {
                CalendarEvent current = provider.read(
                        calendarId(),
                        toDomainScope(normalizedScope),
                        new EventId(eventUid));
                expectedVersion = providerVersionFor(ifMatch, current.version());
            }
            publishEventWriteAudit(normalizedScope, parsed.id(), "update-event", expectedVersion);
            return toResponse(provider.write(new CalendarWrite(
                    parsed,
                    WriteIntent.UPDATE,
                    expectedVersion)), normalizedScope, false);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "update-event");
        }
    }

    public void deleteCalDavEventIcs(String eventUid) {
        deleteCalDavEventIcs(eventUid, CalendarScopeResponse.workspace());
    }

    public void deleteCalDavEventIcs(String eventUid, CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "delete-caldav-event");
        authorization.requireContextPermission(normalizedScope, ContextPermission.EDIT, "delete-event");
        try {
            publishEventWriteAudit(
                    normalizedScope, new EventId(eventUid), "delete-event", EventVersion.unknown());
            calendarProviderPort.delete(
                    calendarId(),
                    toDomainScope(normalizedScope),
                    new EventId(eventUid),
                    EventVersion.unknown());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "delete-event");
        }
    }

    public ApiErrorException reportCalendarQueryNotReady(String reportKind) {
        authorization.requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "caldav-report-" + reportKind);
        return new ApiErrorException(
                HttpStatus.NOT_IMPLEMENTED,
                "caldav-report-not-implemented",
                "CalDAV " + reportKind + " REPORT is reserved but not enabled until query semantics, recurrence, and timezone evidence are complete.",
                Map.of(
                        "module", "calendar",
                        "operation", "caldav-report-" + reportKind,
                        "supportSafe", true,
                        "providerDataPlaneExposed", false));
    }

    public CalendarClientSetupResponse clientSetup() {
        return clientSetup.clientSetup();
    }

    public CalendarNativeSyncSetupResponse nativeSyncSetup(WorkspaceCapabilityStatusResponse readiness) {
        return clientSetup.nativeSyncSetup(readiness);
    }

    public AppleMobileConfigProfile appleMobileConfigProfile() {
        return clientSetup.appleMobileConfigProfile();
    }

    public CalendarAccessPolicyResponse accessPolicy() {
        return clientSetup.accessPolicy();
    }

    public CalendarSetupCredentialListResponse setupCredentials() {
        return clientSetup.setupCredentials();
    }

    public CalendarSetupCredentialResponse createSetupCredential(CalendarSetupCredentialRequest request) {
        return clientSetup.createSetupCredential(request);
    }

    public CalendarSetupCredentialResponse revokeSetupCredential(String credentialId) {
        return clientSetup.revokeSetupCredential(credentialId);
    }

    private List<CalendarScopeResponse> calendarScopes() {
        return List.of(
                CalendarScopeResponse.workspace(),
                CalendarScopeResponse.team("engineering", "Engineering team calendar"),
                CalendarScopeResponse.channel("engineering", "engineering-general", "Engineering / general channel calendar"));
    }

    private CalendarScopeResponse resolveScope(String scopeType, String teamId, String channelId) {
        if (scopeType == null || scopeType.isBlank()) {
            return CalendarScopeResponse.workspace();
        }
        return normalizeScope(new CalendarScopeResponse(
                null,
                scopeType.trim(),
                null,
                "workspace",
                null,
                blankToNull(teamId),
                blankToNull(channelId),
                null,
                List.of()), "list-events");
    }

    private CalendarScopeResponse normalizeScope(CalendarScopeResponse requestedScope, String operation) {
        if (requestedScope == null || requestedScope.type() == null || requestedScope.type().isBlank()) {
            return CalendarScopeResponse.workspace();
        }
        String type = requestedScope.type().trim();
        return switch (type) {
            case "workspace" -> CalendarScopeResponse.workspace();
            case "team" -> {
                String teamId = firstNonBlank(requestedScope.teamId(), "engineering");
                yield CalendarScopeResponse.team(teamId, firstNonBlank(requestedScope.label(), labelForTeam(teamId)));
            }
            case "channel" -> {
                String channelId = firstNonBlank(requestedScope.channelId(), "engineering-general");
                String teamId = firstNonBlank(requestedScope.teamId(), "engineering");
                yield CalendarScopeResponse.channel(
                        teamId,
                        channelId,
                        firstNonBlank(requestedScope.label(), labelForChannel(teamId, channelId)));
            }
            default -> throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "validation-error",
                    "Request validation failed.",
                    Map.of("module", "calendar", "operation", operation,
                            "fields", Map.of("scope.type", "scope type must be workspace, team, or channel")));
        };
    }

    private CalendarEventResponse toResponse(CalendarEvent event, CalendarScopeResponse scope, boolean encodeId) {
        String id = encodeId ? scopedEventId(scope, event.id().value()) : event.id().value();
        return new CalendarEventResponse(
                id,
                event.title(),
                event.description(),
                event.startsAt().toOffsetDateTime(),
                event.endsAt().toOffsetDateTime(),
                event.timezone().getId(),
                event.location(),
                event.allDay(),
                event.version().value(),
                scope,
                null,
                event.attendees().stream()
                        .map(attendee -> new CalendarAttendeeResponse(
                                attendee.displayName(),
                                attendee.address(),
                                attendee.role(),
                                attendee.response()))
                        .toList(),
                null,
                event.updatedAt() == null ? null : OffsetDateTime.ofInstant(event.updatedAt(), ZoneOffset.UTC));
    }

    private CalendarEventResponse eventResponse(CalDavEventResource resource) {
        try {
            CalendarEvent event = icalendarMapper.parse(
                    calendarId(),
                    toDomainScope(resource.scope()),
                    new EventVersion(resource.etag()),
                    resource.calendarData());
            return toResponse(event, resource.scope(), false);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "map-caldav-event");
        }
    }

    private CalendarEvent eventFrom(
            CreateCalendarEventRequest request,
            CalendarScopeResponse scope,
            EventId id) {
        ZoneId timezone = calendarZone(request.timezone());
        return new CalendarEvent(
                calendarId(),
                id,
                toDomainScope(scope),
                request.title(),
                request.description(),
                request.startsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                request.endsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                timezone,
                request.allDay(),
                request.location(),
                List.of(),
                null,
                EventVersion.unknown(),
                null);
    }

    private CalendarEvent merge(
            CalendarEvent existing,
            UpdateCalendarEventRequest request,
            CalendarScopeResponse scope) {
        ZoneId timezone = request.timezone() == null || request.timezone().isBlank()
                ? existing.timezone()
                : calendarZone(request.timezone());
        return new CalendarEvent(
                existing.calendarId(),
                existing.id(),
                toDomainScope(scope),
                request.title() == null ? existing.title() : request.title(),
                request.description() == null ? existing.description() : blankToNull(request.description()),
                request.startsAt() == null
                        ? existing.localStart()
                        : request.startsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                request.endsAt() == null
                        ? existing.localEnd()
                        : request.endsAt().atZoneSameInstant(timezone).toLocalDateTime(),
                timezone,
                request.allDay() == null ? existing.allDay() : request.allDay(),
                request.location() == null ? existing.location() : blankToNull(request.location()),
                existing.attendees(),
                existing.recurrence(),
                existing.version(),
                existing.updatedAt());
    }

    private ZoneId calendarZone(String timezone) {
        try {
            return ZoneId.of(timezone);
        } catch (RuntimeException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "calendar-timezone-invalid",
                    "Calendar event timezone is not supported.",
                    Map.of("module", "calendar", "field", "timezone"));
        }
    }

    private CalendarScope toDomainScope(CalendarScopeResponse scope) {
        CalendarScopeResponse normalized = scope == null ? CalendarScopeResponse.workspace() : scope;
        return switch (normalized.type()) {
            case "team" -> new CalendarScope(ScopeType.TEAM, normalized.teamId(), null);
            case "channel" -> new CalendarScope(ScopeType.CHANNEL, normalized.teamId(), normalized.channelId());
            default -> CalendarScope.workspace();
        };
    }

    private Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private String scopedEventId(CalendarScopeResponse scope, String rawId) {
        if (scope == null || "workspace".equals(scope.type())) {
            return rawId;
        }
        String scopeKey = String.join("|",
                scope.type(),
                scope.teamId() == null ? "" : scope.teamId(),
                scope.channelId() == null ? "" : scope.channelId());
        String encodedScope = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(scopeKey.getBytes(StandardCharsets.UTF_8));
        return "scoped:" + encodedScope + ":" + rawId;
    }

    private ScopedEventId scopedEventId(String id) {
        if (id == null || !id.startsWith("scoped:")) {
            return new ScopedEventId(CalendarScopeResponse.workspace(), id);
        }
        String[] parts = id.split(":", 3);
        if (parts.length != 3) {
            throw invalidScopedEventId();
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            String[] scopeParts = decoded.split("\\|", -1);
            if (scopeParts.length != 3) {
                throw invalidScopedEventId();
            }
            CalendarScopeResponse scope = normalizeScope(new CalendarScopeResponse(
                    null,
                    scopeParts[0],
                    null,
                    "workspace",
                    null,
                    blankToNull(scopeParts[1]),
                    blankToNull(scopeParts[2]),
                    null,
                    List.of()), "read-event");
            return new ScopedEventId(scope, parts[2]);
        } catch (IllegalArgumentException exception) {
            throw invalidScopedEventId();
        }
    }

    private ApiErrorException invalidScopedEventId() {
        return new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "invalid-calendar-event-id",
                "Calendar event id is not a valid Weave calendar facade id.",
                Map.of("module", "calendar"));
    }

    private CalendarEvent parseCalDavEvent(
            String eventUid,
            CalendarScopeResponse scope,
            String calendarData,
            String operation) {
        if (calendarData == null || calendarData.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "calendar-ics-invalid",
                    "Calendar data is not a supported iCalendar VEVENT.",
                    withDefaultDetails(Map.of("reason", "empty-calendar-data"), operation));
        }
        try {
            CalendarEvent event = icalendarMapper.parse(
                    calendarId(),
                    toDomainScope(scope),
                    EventVersion.unknown(),
                    calendarData);
            if (!event.id().value().equals(eventUid)) {
                throw new ApiErrorException(
                        HttpStatus.CONFLICT,
                        "calendar-ics-uid-mismatch",
                        "The iCalendar UID must match the CalDAV event path.",
                        withDefaultDetails(Map.of("supportSafe", true), operation));
            }
            return event;
        } catch (ApiErrorException exception) {
            throw exception;
        } catch (CalendarAdapterException exception) {
            String code = "caldav-recurrence-unsupported".equals(exception.details().get("errorCode"))
                    ? "caldav-recurrence-unsupported"
                    : "calendar-ics-invalid";
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Calendar data is not a supported iCalendar VEVENT.",
                    withDefaultDetails(exception.details(), operation));
        } catch (RuntimeException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "calendar-ics-invalid",
                    "Calendar data is not a supported iCalendar VEVENT.",
                    withDefaultDetails(Map.of(
                            "reason", "invalid-event-data",
                            "supportSafe", true,
                            "providerDataPlaneExposed", false), operation));
        }
    }

    private String labelForTeam(String teamId) {
        return capitalize(teamId) + " team calendar";
    }

    private String labelForChannel(String teamId, String channelId) {
        return capitalize(teamId) + " / " + channelId + " channel calendar";
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "Weave";
        }
        String normalized = value.replace('-', ' ').trim();
        return normalized.substring(0, 1).toUpperCase() + normalized.substring(1);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record ScopedEventId(CalendarScopeResponse scope, String rawId) {
    }

    private CalendarId calendarId() {
        PrincipalContext principal = authorization.currentPrincipalContext();
        return new CalendarId("calendar-" + digest(principal.tenantId()).substring(0, 40));
    }

    private void publishEventWriteAudit(
            CalendarScopeResponse scope,
            EventId eventId,
            String operation,
            EventVersion expectedVersion) {
        PrincipalContext principal = authorization.currentPrincipalContext();
        String contextId = authorization.contextId(scope);
        String subject = String.join("\u0000",
                principal.tenantId(),
                principal.principalRef(),
                contextId,
                operation,
                eventId.value(),
                expectedVersion == null || expectedVersion.value() == null
                        ? "unknown"
                        : expectedVersion.value());
        AuditWriteGate.publishRequired(auditEventPublisher, new AuditEvent(
                principal.tenantId(),
                contextId,
                principal.principalRef(),
                "weave:calendar-facade",
                AuditAction.CALENDAR_EVENT_WRITE_ATTEMPTED,
                clock.instant(),
                AuditAction.CALENDAR_EVENT_WRITE_ATTEMPTED.wireName() + ":" + digest(subject),
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "module", "calendar",
                        "operation", operation,
                        "scopeType", scope.type(),
                        "supportSafe", true,
                        "providerDataPlaneExposed", false)));
    }

    private static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private void validateRange(OffsetDateTime from, OffsetDateTime to) {
        if (from != null && to != null && !to.isAfter(from)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "validation-error",
                    "Request validation failed.",
                    Map.of("fields", Map.of("to", "to must be after from")));
        }
    }

    private ApiErrorException apiError(CalendarAdapterException exception, String fallbackOperation) {
        Map<String, Object> details = withDefaultDetails(exception.details(), fallbackOperation);
        return switch (exception.type()) {
            case NOT_CONFIGURED -> new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "calendar-adapter-not-configured",
                    "Calendar facade is available, but calendar storage is not configured yet.",
                    details);
            case INVALID_REQUEST -> new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "invalid-calendar-event-id",
                    exception.getMessage(),
                    details);
            case AUTH_FAILED -> new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "calendar-storage-auth-failed",
                    "Calendar storage is unavailable because the backend actor is not authorized.",
                    details);
            case NOT_FOUND -> new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "calendar-event-not-found",
                    "Calendar event was not found.",
                    details);
            case CONFLICT -> new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "calendar-event-conflict",
                    "Calendar event changed in storage. Refresh and try again.",
                    details);
            case DOWNSTREAM_UNAVAILABLE, INVALID_RESPONSE -> new ApiErrorException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "calendar-storage-unavailable",
                    "Calendar storage is currently unavailable.",
                    details);
        };
    }

    private EventVersion providerVersionFor(String candidateHeader, EventVersion currentVersion) {
        String current = currentVersion == null ? null : currentVersion.value();
        if (!etagMatches(current, candidateHeader)) {
            throw new ApiErrorException(
                    HttpStatus.PRECONDITION_FAILED,
                    "caldav-precondition-failed",
                    "If-Match did not match the current calendar event state.",
                    Map.of(
                            "module", "calendar",
                            "operation", "update-event",
                            "supportSafe", true,
                            "providerDataPlaneExposed", false,
                            "diagnosticsRedacted", true));
        }
        // The northbound ETag may have been normalized by an HTTP client or
        // proxy. Preserve optimistic concurrency by validating that token at
        // the facade, then send the provider's exact current token southbound.
        return currentVersion;
    }

    private boolean etagMatches(String currentEtag, String candidateHeader) {
        if (currentEtag == null || candidateHeader == null || candidateHeader.isBlank()) {
            return false;
        }
        String current = normalizeEtag(currentEtag);
        for (String candidate : candidateHeader.split(",")) {
            if ("*".equals(candidate.trim()) || current.equals(normalizeEtag(candidate))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeEtag(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.startsWith("W/")) {
            normalized = normalized.substring(2).trim();
        }
        if (normalized.length() >= 2 && normalized.startsWith("\"") && normalized.endsWith("\"")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        return normalized;
    }

    private Map<String, Object> withDefaultDetails(Map<String, Object> details, String operation) {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("module", "calendar");
        merged.put("operation", operation);
        merged.putAll(details);
        return merged;
    }

    private String firstNonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) {
            return preferred;
        }
        return fallback;
    }

}
