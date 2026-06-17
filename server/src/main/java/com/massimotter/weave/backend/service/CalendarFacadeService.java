package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
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
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import com.massimotter.weave.backend.service.calendar.CalendarAdapter;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfile;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfileRenderer;
import com.massimotter.weave.backend.service.calendar.CalendarPrincipal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
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

    private final ObjectProvider<CalendarAdapter> calendarAdapterProvider;
    private final String nextcloudBaseUrl;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final AppleMobileConfigProfileRenderer appleProfileRenderer;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;
    private final Map<String, CalendarSetupCredentialResponse> setupCredentials = new ConcurrentHashMap<>();

    public CalendarFacadeService(
            ObjectProvider<CalendarAdapter> calendarAdapterProvider,
            ContextAuthorizationPort contextAuthorizationPort) {
        this(calendarAdapterProvider, "https://files.weave.test", contextAuthorizationPort,
                new ContextAuthorizationProperties(null, null, null, null, null, null, null, null), null, Clock.systemUTC());
    }

    @Autowired
    public CalendarFacadeService(
            ObjectProvider<CalendarAdapter> calendarAdapterProvider,
            @Value("${weave.platform.nextcloud-base-url:https://files.weave.test}") String nextcloudBaseUrl,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            ObjectProvider<AuditEventPublisher> auditEventPublisherProvider) {
        this(calendarAdapterProvider, nextcloudBaseUrl, contextAuthorizationPort, contextAuthorizationProperties,
                auditEventPublisherProvider.getIfAvailable(), Clock.systemUTC());
    }

    CalendarFacadeService(
            ObjectProvider<CalendarAdapter> calendarAdapterProvider,
            String nextcloudBaseUrl,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.calendarAdapterProvider = calendarAdapterProvider;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties == null
                ? new ContextAuthorizationProperties(null, null, null, null, null, null, null, null)
                : contextAuthorizationProperties;
        this.nextcloudBaseUrl = nextcloudBaseUrl == null || nextcloudBaseUrl.isBlank()
                ? "https://files.weave.test"
                : nextcloudBaseUrl.trim();
        this.appleProfileRenderer = new AppleMobileConfigProfileRenderer(this.nextcloudBaseUrl);
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock == null ? Clock.systemUTC() : clock;
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
        requireContextPermission(scope, ContextPermission.VIEW, "list-events");
        try {
            List<CalendarEventResponse> events = adapter("list-events").list(principal(), scope, from, to).stream()
                    .map(event -> withScope(event, scope, true))
                    .toList();
            return new CalendarEventsResponse(scope, events);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "list-events");
        }
    }

    public CalendarEventResponse create(CreateCalendarEventRequest request) {
        CalendarScopeResponse scope = normalizeScope(request.scope(), "create-event");
        PrincipalContext context = requireContextPermission(scope, ContextPermission.EDIT, "create-event");
        requireAuditPublisher();
        try {
            CalendarEventResponse created = withScope(adapter("create-event").create(principal(), withScope(request, scope)), scope, true);
            publishMutationAudit(context, AuditAction.CALENDAR_EVENT_CREATED, "create-event", scope, created.id());
            return created;
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "create-event");
        }
    }

    public CalendarEventResponse read(String id) {
        ScopedEventId eventId = scopedEventId(id);
        requireContextPermission(eventId.scope(), ContextPermission.VIEW, "read-event");
        try {
            return withScope(adapter("read-event").read(principal(), eventId.scope(), eventId.rawId()), eventId.scope(), true);
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "read-event");
        }
    }

    public CalendarEventResponse update(String id, UpdateCalendarEventRequest request) {
        ScopedEventId eventId = scopedEventId(id);
        CalendarScopeResponse scope = request.scope() == null ? eventId.scope() : normalizeScope(request.scope(), "update-event");
        PrincipalContext context = requireContextPermission(scope, ContextPermission.EDIT, "update-event");
        requireAuditPublisher();
        try {
            CalendarEventResponse updated = withScope(adapter("update-event").update(principal(), scope, eventId.rawId(), request), scope, true);
            publishMutationAudit(context, AuditAction.CALENDAR_EVENT_UPDATED, "update-event", scope, updated.id());
            return updated;
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "update-event");
        }
    }

    public void delete(String id) {
        ScopedEventId eventId = scopedEventId(id);
        PrincipalContext context = requireContextPermission(eventId.scope(), ContextPermission.EDIT, "delete-event");
        requireAuditPublisher();
        try {
            adapter("delete-event").delete(principal(), eventId.scope(), eventId.rawId());
            publishMutationAudit(context, AuditAction.CALENDAR_EVENT_DELETED, "delete-event", eventId.scope(), eventId.rawId());
        } catch (CalendarAdapterException exception) {
            throw apiError(exception, "delete-event");
        }
    }

    public CalendarClientSetupResponse clientSetup() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "client-setup");
        CalendarPrincipal principal = principal();
        String accountReference = "calendar-account:" + principal.subject();

        return new CalendarClientSetupResponse(
                CalendarScopeResponse.workspace(),
                accessModel(),
                credentialReadiness(),
                accountReference,
                "The backend never returns provider passwords, app passwords, bearer tokens, or static profile secrets. "
                        + "User-controlled setup flows must use revocable scoped credentials once available.",
                List.of(
                        new CalendarClientSetupOptionResponse(
                                "apple",
                                "signed-profile",
                                false,
                                null,
                                "Signed profile download remains fail-closed until profile signing and revocable credentials are implemented.",
                                List.of(
                                        "Profile generation must not embed a permanent password or backend service credential.",
                                        "The backend route is reserved for a signed no-secret profile and currently returns 503 rather than serving an unsigned artifact.")),
                        new CalendarClientSetupOptionResponse(
                                "android",
                                "guided-setup",
                                false,
                                null,
                                "Guided setup is blocked until Weave can issue and revoke scoped per-client credentials.",
                                List.of(
                                        "Android setup must use a user-controlled credential flow.",
                                        "Read-only subscriptions remain a separate fallback once scoped feed tokens exist.")),
                        new CalendarClientSetupOptionResponse(
                                "desktop",
                                "guided-setup",
                                false,
                                null,
                                "Desktop setup is blocked until Weave can issue and revoke scoped per-client credentials.",
                                List.of(
                                        "Desktop setup must not require provider-specific discovery details from the member API.",
                                        "Support-only diagnostics may expose technical routing data outside the member contract.")),
                        new CalendarClientSetupOptionResponse(
                                "subscription",
                                "read-only-feed",
                                false,
                                null,
                                "A revocable read-only feed token is not implemented yet.",
                                List.of(
                                        "Read-only feeds are one-way subscription/download, not full two-way sync.",
                                        "Feeds become available only with scoped tokens and revocation."))));
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
                                "Revocable scoped per-client credential issuance remains a prerequisite for password-bearing profiles.",
                                "Unsigned profiles are deliberately not downloadable from the authenticated API.")));
    }

    public CalendarAccessPolicyResponse accessPolicy() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "access-policy");
        return new CalendarAccessPolicyResponse(
                accessModel(),
                List.of("workspace-calendar.read", "workspace-calendar.write"),
                List.of("private-personal-calendar.read", "backend-actor-private-personal-calendar.read"),
                List.of(
                        "Choose and document a private calendar access model: user sharing/provisioning, delegated token exchange, or a Weave token bridge.",
                        "Add operator diagnostics proving private calendar templates are explicitly authorized.",
                        "Add revocation and audit tests before any private personal calendar access path is enabled."),
                false);
    }

    public CalendarSetupCredentialListResponse setupCredentials() {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.VIEW, "list-setup-credentials");
        CalendarPrincipal principal = principal();
        return new CalendarSetupCredentialListResponse(setupCredentials.values().stream()
                .filter(credential -> principal.subject().equals(credential.username()))
                .sorted(java.util.Comparator.comparing(CalendarSetupCredentialResponse::issuedAt))
                .toList());
    }

    public CalendarSetupCredentialResponse createSetupCredential(CalendarSetupCredentialRequest request) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "create-setup-credential");
        CalendarPrincipal principal = principal();
        OffsetDateTime issuedAt = OffsetDateTime.now(ZoneOffset.UTC);
        String id = "cal_setup_" + UUID.randomUUID();
        CalendarSetupCredentialResponse credential = new CalendarSetupCredentialResponse(
                id,
                "active-no-secret-issued",
                principal.subject(),
                defaultIfBlank(request.clientType(), "caldav"),
                defaultIfBlank(request.label(), "Calendar client setup"),
                issuedAt,
                issuedAt.plusHours(24),
                null,
                false,
                false,
                List.of("DELETE /api/calendar/client-setup/credentials/" + id));
        setupCredentials.put(id, credential);
        return credential;
    }

    public CalendarSetupCredentialResponse revokeSetupCredential(String credentialId) {
        requireContextPermission(CalendarScopeResponse.workspace(), ContextPermission.EDIT, "revoke-setup-credential");
        CalendarPrincipal principal = principal();
        CalendarSetupCredentialResponse current = setupCredentials.get(credentialId);
        if (current == null || !principal.subject().equals(current.username())) {
            throw new ApiErrorException(
                    HttpStatus.NOT_FOUND,
                    "calendar-setup-credential-not-found",
                    "Calendar setup credential was not found.",
                    Map.of("module", "calendar", "operation", "revoke-setup-credential"));
        }
        CalendarSetupCredentialResponse revoked = new CalendarSetupCredentialResponse(
                current.credentialId(),
                "revoked",
                current.username(),
                current.clientType(),
                current.label(),
                current.issuedAt(),
                current.expiresAt(),
                OffsetDateTime.now(ZoneOffset.UTC),
                false,
                false,
                List.of());
        setupCredentials.put(credentialId, revoked);
        return revoked;
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

    private CalendarEventResponse withScope(CalendarEventResponse event, CalendarScopeResponse scope, boolean encodeId) {
        String id = encodeId ? scopedEventId(scope, event.id()) : event.id();
        return new CalendarEventResponse(
                id,
                event.title(),
                event.description(),
                event.startsAt(),
                event.endsAt(),
                event.timezone(),
                event.location(),
                event.allDay(),
                event.etag(),
                scope,
                null,
                event.attendees(),
                null,
                event.updatedAt());
    }

    private CreateCalendarEventRequest withScope(CreateCalendarEventRequest request, CalendarScopeResponse scope) {
        return new CreateCalendarEventRequest(
                request.title(),
                request.description(),
                request.startsAt(),
                request.endsAt(),
                request.timezone(),
                request.location(),
                request.allDay(),
                scope);
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

    private CalendarAccessModelResponse accessModel() {
        return new CalendarAccessModelResponse(
                "workspace-team-channel-calendar",
                "workspace-team-channel",
                false,
                "Private per-user calendars are not exposed until provisioning, sharing, or delegated-token access is specified and tested.",
                "user-controlled setup uses revocable scoped credentials; backend actor credentials are never issued to clients",
                List.of(
                        "The product calendar facade exposes workspace, team, and channel scope metadata.",
                        "Backend calendar configuration that targets arbitrary private personal calendars must stay fail-closed.",
                        "User-controlled setup flows must not receive backend actor credentials or provider-specific routing data from the member contract."));
    }

    private String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private CalendarCredentialReadinessResponse credentialReadiness() {
        return new CalendarCredentialReadinessResponse(
                "blocked_until_revocable_credentials",
                false,
                false,
                false,
                false,
                false,
                List.of(
                        "Signed Apple .mobileconfig generation is not implemented yet.",
                        "Weave-issued revocable scoped per-client credentials are not implemented yet.",
                        "Read-only ICS/webcal feed tokens are not implemented yet."));
    }

    private CalendarAdapter adapter(String operation) {
        CalendarAdapter adapter = calendarAdapterProvider.getIfAvailable();
        if (adapter == null) {
            throw adapterNotConfigured(operation);
        }
        return adapter;
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
            String nextcloudUserId = firstNonBlank(jwt.getClaimAsString("preferred_username"), jwt.getSubject());
            return new CalendarPrincipal(jwt.getSubject(), nextcloudUserId);
        }
        return new CalendarPrincipal(authentication.getName(), authentication.getName());
    }

    private PrincipalContext requireContextPermission(
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
        return principalContext;
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

    private void requireAuditPublisher() {
        if (auditEventPublisher == null) {
            throw new com.massimotter.weave.backend.audit.AuditRequiredException("audit publisher is required before calendar mutations are allowed");
        }
    }

    private void publishMutationAudit(PrincipalContext context, AuditAction action, String operation, CalendarScopeResponse scope, String canonicalEventId) {
        String stableRef = stableAuditRef(scope.contextId() + ":" + canonicalEventId);
        AuditWriteGate.publishRequired(auditEventPublisher, new AuditEvent(
                context.tenantId(),
                scope.contextId(),
                context.principalRef(),
                "calendar-facade",
                action,
                clock.instant(),
                "calendar:" + operation + ":" + stableRef,
                AuditRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "module", "calendar",
                        "operation", operation,
                        "canonicalEventId", stableRef,
                        "mappingRef", "provider-mapping://calendar/" + stableRef,
                        "contextId", scope.contextId(),
                        "scopeType", scope.type())));
    }

    private String stableAuditRef(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return Integer.toHexString(source.hashCode());
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
                    "Calendar storage is unavailable because the backend service is not authorized.",
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
