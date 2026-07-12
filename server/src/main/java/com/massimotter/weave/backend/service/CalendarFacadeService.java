package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee;
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
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.model.calendar.CalendarAccessModelResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.model.calendar.CalendarClientSetupOptionResponse;
import com.massimotter.weave.backend.model.calendar.CalendarClientSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarCredentialReadinessResponse;
import com.massimotter.weave.backend.model.calendar.CalendarExternalEndpointsResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAttendeeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CalendarNativeSyncOptionResponse;
import com.massimotter.weave.backend.model.calendar.CalendarNativeSyncSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfile;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfileRenderer;
import com.massimotter.weave.backend.service.calendar.CalDavEventResource;
import com.massimotter.weave.backend.service.calendar.CalDavSyncResult;
import com.massimotter.weave.backend.service.calendar.CalendarPrincipal;
import com.massimotter.weave.backend.service.calendar.IcalendarMapper;
import com.massimotter.weave.backend.security.device.DeviceCredential;
import com.massimotter.weave.backend.security.device.DeviceCredentialException;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class CalendarFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final ObjectProvider<CalendarProviderPort> calendarProviderPortProvider;
    private final String calDavPublicBaseUrl;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AppleMobileConfigProfileRenderer appleProfileRenderer;
    private final DeviceCredentialService deviceCredentialService;
    private final IcalendarMapper icalendarMapper = new IcalendarMapper();
    private final Map<String, CalendarSyncCursor> syncCursors = new ConcurrentHashMap<>();

    @Autowired
    public CalendarFacadeService(
            ObjectProvider<CalendarProviderPort> calendarProviderPortProvider,
            @Value("${weave.calendar.caldav.public-base-url:https://calendar.weave.test}") String calDavPublicBaseUrl,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            DeviceCredentialService deviceCredentialService,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.calendarProviderPortProvider = calendarProviderPortProvider;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties == null
                ? new ContextAuthorizationProperties(null, null, null, null, null, null, null, null)
                : contextAuthorizationProperties;
        this.calDavPublicBaseUrl = calDavPublicBaseUrl == null || calDavPublicBaseUrl.isBlank()
                ? "https://calendar.weave.test"
                : calDavPublicBaseUrl.trim();
        this.appleProfileRenderer = new AppleMobileConfigProfileRenderer(this.calDavPublicBaseUrl);
        this.deviceCredentialService = deviceCredentialService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    public CalendarScopesResponse scopes() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "list-scopes");
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
        requireContextPermission(normalizedScope, ContextPermission.VIEW, "list-events");
        try {
            return adapter("list-events")
                    .query(calendarId(), toDomainScope(normalizedScope), instant(from), instant(to)).stream()
                    .map(event -> calDavResource(event, normalizedScope))
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
        requireContextPermission(normalizedScope, ContextPermission.VIEW, "free-busy");
        try {
            return adapter("free-busy").freeBusy(
                    calendarId(),
                    toDomainScope(normalizedScope),
                    from.toInstant(),
                    to.toInstant());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "free-busy");
        }
    }

    public CalDavSyncResult syncCalDavResources(CalendarScopeResponse scope, String weaveSyncToken) {
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "caldav-sync-collection");
        requireContextPermission(normalizedScope, ContextPermission.VIEW, "sync-events");
        CalendarId calendarId = calendarId();
        String providerToken = providerSyncToken(weaveSyncToken, calendarId, normalizedScope);
        try {
            var changeSet = adapter("sync-events").changes(
                    calendarId,
                    toDomainScope(normalizedScope),
                    providerToken);
            List<CalDavEventResource> changed = changeSet.changes().stream()
                    .filter(change -> !change.deleted())
                    .map(change -> adapter("read-event").read(
                            calendarId,
                            toDomainScope(normalizedScope),
                            change.eventId()))
                    .map(event -> calDavResource(event, normalizedScope))
                    .toList();
            List<String> deleted = changeSet.changes().stream()
                    .filter(com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange::deleted)
                    .map(change -> change.eventId().value())
                    .toList();
            String issuedToken = "weave-caldav-sync-" + UUID.randomUUID();
            syncCursors.put(issuedToken, new CalendarSyncCursor(
                    calendarId,
                    normalizedScope.id(),
                    changeSet.syncToken()));
            return new CalDavSyncResult(issuedToken, normalizedScope, changed, deleted);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "sync-events");
        }
    }

    public CalendarEventResponse create(CreateCalendarEventRequest request) {
        CalendarScopeResponse scope = normalizeScope(request.scope(), "create-event");
        requireContextPermission(scope, ContextPermission.EDIT, "create-event");
        try {
            CalendarEvent event = eventFrom(request, scope, new EventId(UUID.randomUUID() + "@weave.test"));
            return toResponse(
                    adapter("create-event").write(new CalendarWrite(event, WriteIntent.CREATE, EventVersion.unknown())),
                    scope,
                    true);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "create-event");
        }
    }

    public CalendarEventResponse read(String id) {
        ScopedEventId eventId = scopedEventId(id);
        requireContextPermission(eventId.scope(), ContextPermission.VIEW, "read-event");
        try {
            return toResponse(adapter("read-event").read(
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
        requireContextPermission(scope, ContextPermission.EDIT, "update-event");
        try {
            CalendarProviderPort adapter = adapter("update-event");
            CalendarEvent existing = adapter.read(calendarId(), toDomainScope(scope), new EventId(eventId.rawId()));
            CalendarEvent updated = merge(existing, request, scope);
            EventVersion expected = request.etag() == null
                    ? EventVersion.unknown()
                    : new EventVersion(request.etag());
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
        requireContextPermission(eventId.scope(), ContextPermission.EDIT, "delete-event");
        try {
            adapter("delete-event").delete(
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
        requireContextPermission(normalizedScope, ContextPermission.VIEW, "read-event");
        try {
            return calDavResource(adapter("read-event").read(
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
            requireContextPermission(normalizedScope, ContextPermission.EDIT, "create-event");
            try {
                return toResponse(adapter("create-event").write(new CalendarWrite(
                        parsed,
                        WriteIntent.CREATE,
                        EventVersion.unknown())), normalizedScope, false);
            } catch (CalendarAdapterException exception) {
                throw apiError(exception, "create-event");
            }
        }
        requireContextPermission(normalizedScope, ContextPermission.EDIT, "update-event");
        try {
            return toResponse(adapter("update-event").write(new CalendarWrite(
                    parsed,
                    WriteIntent.UPDATE,
                    new EventVersion(ifMatch))), normalizedScope, false);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "update-event");
        }
    }

    public void deleteCalDavEventIcs(String eventUid) {
        deleteCalDavEventIcs(eventUid, CalendarScopeResponse.workspace());
    }

    public void deleteCalDavEventIcs(String eventUid, CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = normalizeScope(scope, "delete-caldav-event");
        requireContextPermission(normalizedScope, ContextPermission.EDIT, "delete-event");
        try {
            adapter("delete-event").delete(
                    calendarId(),
                    toDomainScope(normalizedScope),
                    new EventId(eventUid),
                    EventVersion.unknown());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "delete-event");
        }
    }

    public ApiErrorException reportCalendarQueryNotReady(String reportKind) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "caldav-report-" + reportKind);
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
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "client-setup");
        CalendarPrincipal principal = principal();
        String username = principal.userId();
        String discoveryUrl = "/caldav";
        String principalUrl = "/caldav/principals/users/" + strictPathSegment(username) + "/";

        return new CalendarClientSetupResponse(
                CalendarScopeResponse.workspace(),
                accessModel(),
                credentialReadiness(),
                username,
                new CalendarExternalEndpointsResponse("/caldav", discoveryUrl, principalUrl),
                "The backend returns a Weave-issued device secret only in the create response. "
                        + "Stored/listed credentials expose no secret, and provider credentials or endpoints are never returned.",
                List.of(
                        new CalendarClientSetupOptionResponse(
                                "apple",
                                "mobileconfig",
                                false,
                                null,
                                "Signed .mobileconfig download remains fail-closed until profile signing is implemented.",
                                List.of(
                                        "iOS, iPadOS, and macOS can install a CalDAV configuration profile with host, port, SSL, principal URL, and username.",
                                        "Profile generation must not embed a permanent password or backend service credential in the profile.",
                                        "The backend route is reserved for a signed no-secret profile and currently returns 503 rather than serving an unsigned artifact.")),
                        new CalendarClientSetupOptionResponse(
                                "android",
                                "sync-adapter",
                                false,
                                null,
                                "Android Calendar setup waits for the Weave Account/SyncAdapter implementation.",
                                List.of(
                                        "Android has no universal native CalDAV account profile equivalent.",
                                        "The target path is a Weave account plus SyncAdapter that writes through the Weave calendar facade.",
                                        "Webcal/ICS subscriptions are read-only and should remain a separate fallback once scoped feed tokens exist.")),
                        new CalendarClientSetupOptionResponse(
                                "desktop",
                                "caldav-manual",
                                true,
                                "/api/calendar/client-setup/credentials",
                                null,
                                List.of(
                                        "Create a scoped credential, then use the Weave CalDAV discovery path in Thunderbird, Apple Calendar, GNOME, or KDE calendar clients.",
                                        "Microsoft Outlook generally needs an add-in for CalDAV; read-only ICS/webcal can be offered later where acceptable.",
                                        "Use the returned credential ID as the username and its one-time Weave secret as the password.")),
                        new CalendarClientSetupOptionResponse(
                                "subscription",
                                "webcal-ics",
                                false,
                                null,
                                "A revocable read-only ICS/webcal feed token is not implemented yet.",
                                List.of(
                                        "ICS/webcal is one-way subscription/download, not full two-way CalDAV sync.",
                        "It is useful for clients without CalDAV support once scoped feed tokens and revocation are available."))));
    }

    public CalendarNativeSyncSetupResponse nativeSyncSetup(WorkspaceCapabilityStatusResponse readiness) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "native-sync-setup");
        return new CalendarNativeSyncSetupResponse(
                readiness,
                true,
                false,
                false,
                "/caldav",
                "/api/calendar/client-setup/credentials",
                "/api/calendar/client-setup/apple.mobileconfig",
                "/caldav/{scopePath}/{eventUid}.ics",
                List.of(
                        new CalendarNativeSyncOptionResponse(
                                "ios",
                                "CalDAVConfigurationProfile",
                                "pigeon-or-platform-channel",
                                false,
                                "profile_contract_ready_signing_blocked",
                                "open-weave-calendar-native-setup",
                                List.of(
                                        "ios-signed-mobileconfig",
                                        "weave-caldav-compatible-facade",
                                        "scoped-revocable-device-credential",
                                        "physical-ios-calendar-sync-evidence"),
                                List.of(
                                        "iOS setup uses a Weave-hosted profile flow and system Calendar account semantics.",
                                        "The profile route remains fail-closed until signing and scoped credential issuance are configured.",
                                        "Event sync must target the Weave calendar facade, not a storage provider account.")),
                        new CalendarNativeSyncOptionResponse(
                                "android",
                                "CalendarContractAccountSyncAdapter",
                                "pigeon-or-platform-channel",
                                false,
                                "sync_adapter_contract_ready_implementation_blocked",
                                "open-weave-calendar-native-setup",
                                List.of(
                                        "android-account-authenticator",
                                        "android-sync-adapter",
                                        "calendar-provider-sync-identity",
                                        "scoped-revocable-device-credential",
                                        "android-instrumentation-sync-evidence"),
                                List.of(
                                        "Android setup uses a Weave account plus SyncAdapter boundary for Calendar Provider writes.",
                                        "Flutter may start setup, show status, and revoke only.",
                                        "Calendar rows and sync state stay in the native provider layer and Weave facade."))),
                List.of(
                        "OPTIONS /caldav",
                        "PROPFIND /caldav",
                        "REPORT /caldav calendar-query",
                        "REPORT /caldav free-busy-query",
                        "GET /caldav/{scopePath}/{eventUid}.ics",
                        "PUT /caldav/{scopePath}/{eventUid}.ics",
                        "DELETE /caldav/{scopePath}/{eventUid}.ics",
                        "POST /api/calendar/client-setup/credentials",
                        "DELETE /api/calendar/client-setup/credentials/{credentialId}",
                        "GET /api/calendar/client-setup/apple.mobileconfig"),
                List.of(
                        "Signed Apple profile delivery is not configured yet.",
                        "Android Account/SyncAdapter implementation is not wired yet.",
                        "Scoped native device credential issuance currently returns no secret material.",
                        "Physical-device sync and revoke evidence is still required."));
    }


    public AppleMobileConfigProfile appleMobileConfigProfile() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "download-apple-mobileconfig");
        // Keep the download route present but unavailable until a real signing path is wired.
        // The unsigned renderer is covered by tests so the future signer has a no-secret input artifact.
        appleProfileRenderer.renderUnsignedNoSecretProfile(principal());
        throw new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "calendar-apple-profile-unavailable",
                "Apple Calendar profile download is not available until signed no-secret profile generation is configured.",
                Map.of(
                        "module", "calendar",
                        "operation", "download-apple-mobileconfig",
                        "requiresSignedProfile", true,
                        "passwordIncluded", false,
                        "backendActorCredentialsExposed", false,
                        "blockers", List.of(
                                "Profile signing is not configured or implemented in this backend slice.",
                                "Revocable per-client CalDAV credential issuance remains a prerequisite for password-bearing profiles.",
                                "Unsigned profiles are deliberately not downloadable from the authenticated API.")));
    }

    public CalendarAccessPolicyResponse accessPolicy() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "access-policy");
        return new CalendarAccessPolicyResponse(
                accessModel(),
                List.of("workspace-calendar.read", "workspace-calendar.write", "client-setup.metadata"),
                List.of("private-personal-calendar.read", "backend-actor-private-personal-calendar.read"),
                List.of(
                        "Choose and document a private calendar access model: user sharing/provisioning, Nextcloud Login Flow/app password, delegated token exchange, or a Weave token bridge.",
                        "Add operator diagnostics proving private calendar templates are explicitly authorized.",
                        "Add revocation and audit tests before any private personal CalDAV endpoint is enabled."),
                false);
    }

    public CalendarSetupCredentialListResponse setupCredentials() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "list-setup-credentials");
        PrincipalContext principal = currentPrincipalContext();
        return new CalendarSetupCredentialListResponse(deviceCredentialService
                .list("calendar", principal.principalRef()).stream()
                .map(credential -> calendarCredentialResponse(credential, null))
                .toList());
    }

    public CalendarSetupCredentialResponse createSetupCredential(CalendarSetupCredentialRequest request) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "create-setup-credential");
        CalendarPrincipal principal = principal();
        PrincipalContext context = currentPrincipalContext();
        var issued = deviceCredentialService.issue(
                "calendar",
                context.tenantId(),
                context.principalRef(),
                principal.subject(),
                principal.userId(),
                request.clientType(),
                request.label(),
                Set.of("calendar.read", "calendar.manage_events"));
        return calendarCredentialResponse(issued.credential(), issued.secret());
    }

    public CalendarSetupCredentialResponse revokeSetupCredential(String credentialId) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "revoke-setup-credential");
        PrincipalContext principal = currentPrincipalContext();
        DeviceCredential revoked;
        try {
            revoked = deviceCredentialService.revoke("calendar", credentialId, principal.principalRef());
        } catch (DeviceCredentialException exception) {
            throw new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "calendar-setup-credential-not-found",
                    "Calendar setup credential was not found.",
                    Map.of("module", "calendar", "operation", "revoke-setup-credential"));
        }
        return calendarCredentialResponse(revoked, null);
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

    private CalDavEventResource calDavResource(CalendarEvent event, CalendarScopeResponse scope) {
        return new CalDavEventResource(
                event.id().value(),
                scope,
                event.version().value(),
                icalendarMapper.toNorthboundIcalendar(event, scope),
                event.startsAt().toOffsetDateTime(),
                event.endsAt().toOffsetDateTime());
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

    private String providerSyncToken(
            String weaveSyncToken,
            CalendarId calendarId,
            CalendarScopeResponse scope) {
        if (weaveSyncToken == null || weaveSyncToken.isBlank()) {
            return null;
        }
        CalendarSyncCursor cursor = syncCursors.get(weaveSyncToken.trim());
        if (cursor == null
                || !cursor.calendarId().equals(calendarId)
                || !cursor.scopeId().equals(scope.id())) {
            throw new ApiErrorException(
                    HttpStatus.CONFLICT,
                    "caldav-sync-token-invalid",
                    "The CalDAV sync token is invalid or no longer belongs to this calendar scope.",
                    Map.of(
                            "module", "calendar",
                            "operation", "caldav-sync-collection",
                            "supportSafe", true));
        }
        return cursor.providerToken();
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
        } catch (CalendarAdapterException exception) {
            String code = "caldav-recurrence-unsupported".equals(exception.details().get("errorCode"))
                    ? "caldav-recurrence-unsupported"
                    : "calendar-ics-invalid";
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    code,
                    "Calendar data is not a supported iCalendar VEVENT.",
                    withDefaultDetails(exception.details(), operation));
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

    private static String strictPathSegment(String value) {
        StringBuilder encoded = new StringBuilder();
        value.codePoints().forEach(codePoint -> {
            if (isUnreservedPathCharacter(codePoint)) {
                encoded.appendCodePoint(codePoint);
                return;
            }
            byte[] bytes = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8);
            for (byte current : bytes) {
                encoded.append(String.format("%%%02X", current & 0xFF));
            }
        });
        return encoded.toString();
    }

    private static boolean isUnreservedPathCharacter(int codePoint) {
        return (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '-'
                || codePoint == '.'
                || codePoint == '_'
                || codePoint == '~';
    }

    private record ScopedEventId(CalendarScopeResponse scope, String rawId) {
    }

    private record CalendarSyncCursor(CalendarId calendarId, String scopeId, String providerToken) {
    }

    private CalendarAccessModelResponse accessModel() {
        return new CalendarAccessModelResponse(
                "workspace-team-channel-calendar",
                "workspace-team-channel",
                false,
                "Private per-user CalDAV calendars are not exposed until provisioning, sharing, or delegated-token access is specified and tested.",
                "external clients use user-owned revocable per-client credentials; backend actor credentials are never issued to clients",
                List.of(
                        "The product calendar facade exposes workspace, team, and channel scope metadata.",
                        "Backend CalDAV configuration that targets arbitrary private personal calendars must stay fail-closed.",
                        "External clients may use Weave CalDAV discovery paths only after scoped credentials and revoke evidence exist."));
    }

    private CalendarCredentialReadinessResponse credentialReadiness() {
        return new CalendarCredentialReadinessResponse(
                "revocable_credentials_ready",
                false,
                false,
                true,
                false,
                false,
                List.of(
                        "Signed Apple .mobileconfig generation is not implemented yet.",
                        "Read-only ICS/webcal feed tokens are not implemented yet."));
    }

    private CalendarSetupCredentialResponse calendarCredentialResponse(
            DeviceCredential credential,
            String secret) {
        boolean active = credential.activeAt(Instant.now());
        String state = credential.revokedAt() != null ? "revoked" : active ? "active" : "expired";
        return new CalendarSetupCredentialResponse(
                credential.credentialId(),
                state,
                credential.credentialId(),
                credential.principalRef(),
                credential.clientType(),
                credential.label(),
                OffsetDateTime.ofInstant(credential.issuedAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(credential.expiresAt(), ZoneOffset.UTC),
                credential.revokedAt() == null
                        ? null
                        : OffsetDateTime.ofInstant(credential.revokedAt(), ZoneOffset.UTC),
                secret != null,
                secret,
                false,
                active ? List.of("DELETE /api/calendar/client-setup/credentials/" + credential.credentialId()) : List.of());
    }

    private CalendarProviderPort adapter(String operation) {
        CalendarProviderPort adapter = calendarProviderPortProvider.getIfAvailable();
        if (adapter == null) {
            throw adapterNotConfigured(operation);
        }
        return adapter;
    }

    private CalendarId calendarId() {
        return new CalendarId(principal().userId());
    }

    private CalendarPrincipal principal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar"));
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            String userId = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            return new CalendarPrincipal(jwt.getSubject(), userId);
        }
        return new CalendarPrincipal(authentication.getName(), authentication.getName());
    }

    private void requireContextPermission(
            CalendarScopeResponse scope,
            ContextPermission permission,
            String operation) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar", "operation", operation));
        }
        requireCalendarCapability(authentication, permission, operation);
        PrincipalContext principalContext = principalContext(authentication);
        String contextId = contextId(scope);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principalContext.tenantId(),
                contextId,
                principalContext.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw new ApiErrorException(
                    HttpStatus.FORBIDDEN,
                    "calendar-forbidden",
                    "Calendar access is not allowed for this Context/Space.",
                    Map.of(
                            "module", "calendar",
                            "operation", operation,
                            "reason", decision.reason(),
                            "contextId", contextId,
                            "permission", permission.name().toLowerCase()));
        }
    }

    private void requireCalendarCapability(
            Authentication authentication,
            ContextPermission permission,
            String operation) {
        if (!(authentication.getPrincipal() instanceof Jwt jwt)) {
            throw invalidAuthentication("JWT principal is required");
        }
        String required = permission == ContextPermission.VIEW
                ? "calendar.read"
                : "calendar.manage_events";
        workspaceCapabilityService.requireCapability(jwt, required, "calendar", operation);
    }

    private PrincipalContext principalContext(Authentication authentication) {
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return new PrincipalContext(jwtTenantId(jwt), jwtPrincipalRef(jwt));
        }
        String principalRef = contextAuthorizationProperties.principalRef(authentication.getName());
        if (principalRef == null) {
            throw invalidAuthentication("principal claim is missing");
        }
        return new PrincipalContext(contextAuthorizationProperties.defaultTenantId(), principalRef);
    }

    private PrincipalContext currentPrincipalContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            throw new ApiErrorException(
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required.",
                    Map.of("module", "calendar"));
        }
        return principalContext(authentication);
    }

    private String jwtTenantId(Jwt jwt) {
        String tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantClaim());
        if (tenantId == null) {
            tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantFallbackClaim());
        }
        if (tenantId == null) {
            throw invalidAuthentication("tenant claim is missing");
        }
        return tenantId;
    }

    private String jwtPrincipalRef(Jwt jwt) {
        String configuredClaim = jwtClaim(jwt, contextAuthorizationProperties.principalClaim());
        if (configuredClaim != null) {
            return contextAuthorizationProperties.principalRef(configuredClaim);
        }
        String principalRef = contextAuthorizationProperties.principalRef(jwt.getSubject());
        if (principalRef == null) {
            throw invalidAuthentication("principal claim is missing");
        }
        return principalRef;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiErrorException invalidAuthentication(String reason) {
        return new ApiErrorException(
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Authentication is required.",
                Map.of("module", "calendar", "reason", reason));
    }

    private String contextId(CalendarScopeResponse scope) {
        CalendarScopeResponse normalizedScope = scope == null ? CalendarScopeResponse.workspace() : scope;
        return switch (normalizedScope.type() == null ? "workspace" : normalizedScope.type()) {
            case "team" -> "team-" + firstNonBlank(normalizedScope.teamId(), "engineering");
            case "channel" -> "channel-" + firstNonBlank(normalizedScope.channelId(), "engineering-general");
            default -> DEFAULT_CONTEXT_ID;
        };
    }

    private record PrincipalContext(String tenantId, String principalRef) {
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

    private ApiErrorException adapterNotConfigured(String operation) {
        return new ApiErrorException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "calendar-adapter-not-configured",
                "Calendar facade is available, but calendar storage is not configured yet.",
                Map.of("module", "calendar", "operation", operation));
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
