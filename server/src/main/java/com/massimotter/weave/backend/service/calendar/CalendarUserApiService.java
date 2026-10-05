package com.massimotter.weave.backend.service.calendar;

import com.massimotter.weave.backend.audit.*;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.*;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import com.massimotter.weave.backend.providerbinding.domain.*;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

/** User operations over one current Calendar authority; no provider payload is persisted here. */
@Service
public class CalendarUserApiService {
    public static final String CONFIGURATION_REF = "configuration:calendar:deployment";
    private static final String DOMAIN = "calendar";
    private final DeploymentOrganizationAdmission admission;
    private final OrganizationIdentityContextResolver identities;
    private final ContextAuthorizationProperties context;
    private final ContextAuthorizationPort authorization;
    private final WorkspaceCapabilityService capabilities;
    private final ProviderBindingRepository bindings;
    private final Map<String, CalendarProviderPort> providers;
    private final AuditEventPublisher audit;
    private final CalendarOccurrenceEngine occurrences = new CalendarOccurrenceEngine(new Ical4jRecurrenceEngine());

    public CalendarUserApiService(DeploymentOrganizationAdmission admission, OrganizationIdentityContextResolver identities,
            ContextAuthorizationProperties context, ContextAuthorizationPort authorization, WorkspaceCapabilityService capabilities,
            ProviderBindingRepository bindings, List<CalendarProviderPort> providers, AuditEventPublisher audit) {
        this.admission = admission; this.identities = identities; this.context = context; this.authorization = authorization;
        this.capabilities = capabilities; this.bindings = bindings; this.audit = audit;
        Map<String, CalendarProviderPort> keyed = new HashMap<>();
        for (CalendarProviderPort provider : providers) {
            if (keyed.putIfAbsent(provider.conformanceProfile().adapterKey(), provider) != null) {
                throw new IllegalStateException("Duplicate Calendar adapter key");
            }
        }
        this.providers = Map.copyOf(keyed);
    }

    public Calendars calendars(Jwt jwt) {
        Member member = member(jwt, false);
        bound(member);
        return new Calendars(scopes(member.organization()).stream()
                .filter(scope -> allowed(member, scope, ContextPermission.VIEW))
                .map(scope -> new CalendarUserModels.Calendar(calendarRef(member, scope), transportScope(scope), actions(member, scope)))
                .toList());
    }

    public Agenda agenda(Jwt jwt, String calendarRef, Instant from, Instant to, String evaluationTimeZone) {
        if (from == null || to == null || !from.isBefore(to) || Duration.between(from, to).compareTo(Duration.ofDays(366)) > 0) throw invalid();
        ZoneId zone;
        Instant queryFrom;
        Instant queryTo;
        try {
            zone = CalendarUserModelMapper.zone(evaluationTimeZone);
            queryFrom = from.minus(Duration.ofDays(2));
            queryTo = to.plus(Duration.ofDays(2));
            // Provider and recurrence libraries operate on calendar dates, not the wider Instant range.
            if (queryFrom.atZone(zone).getYear() < 1 || queryTo.atZone(zone).plusDays(1).getYear() > 9999) throw invalid();
        } catch (IllegalArgumentException | DateTimeException invalid) { throw invalid(); }
        Member member = member(jwt, false);
        CalendarScope scope = scope(member, calendarRef, ContextPermission.VIEW);
        Bound bound = bound(member);
        // Provider DATE/FLOATING query zones can differ; the exact member window is evaluated below.
        List<CalendarEvent> candidates = provider(() -> bound.provider().query(providerCalendar(member), scope,
                queryFrom, queryTo));
        if (candidates.size() > 1000) throw tooLarge();
        List<Event> events = new ArrayList<>();
        List<Occurrence> projected = new ArrayList<>();
        for (CalendarEvent event : candidates) {
            verifyEvent(member, scope, event, null);
            List<CalendarOccurrence> expanded = provider(() -> occurrences.occurrences(event, from, to, zone));
            if (expanded.size() >= 10000 || projected.size() + expanded.size() > 10000) throw tooLarge();
            if (expanded.isEmpty()) continue;
            String id = identity(member, bound, scope, event.id(), null);
            events.add(project(member, bound, scope, id, event));
            expanded.forEach(value -> projected.add(new Occurrence(id, value.start().toInstant(), value.end().toInstant())));
        }
        current(member, bound);
        projected.sort(Comparator.comparing(Occurrence::startsAt).thenComparing(Occurrence::eventId));
        return new Agenda(calendarRef, from, to, zone.getId(), List.copyOf(events), List.copyOf(projected));
    }

    public Event read(Jwt jwt, String calendarRef, String eventRef) {
        Member member = member(jwt, false);
        CalendarScope scope = scope(member, calendarRef, ContextPermission.VIEW);
        Bound bound = bound(member);
        CalendarEvent event = readMapped(member, bound, scope, eventRef);
        current(member, bound);
        return project(member, bound, scope, eventRef, event);
    }

    public Event create(Jwt jwt, String calendarRef, WriteRequest request, String idempotencyKey) {
        Member member = member(jwt, true);
        CalendarScope scope = scope(member, calendarRef, ContextPermission.EDIT);
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._:-]{16,128}")) throw invalid();
        Bound bound = bound(member);
        String identity = digest(member.organization() + "\0" + calendarRef + "\0" + member.principal() + "\0" + idempotencyKey);
        String eventRef = "event:" + identity;
        EventId providerId = new EventId("weave-" + identity + "@calendar");
        CalendarEvent incoming = content(member, scope, providerId, request);
        identity(member, bound, scope, providerId, eventRef);
        current(member, bound);
        audit(member, scope, eventRef, "create", "new");
        CalendarEvent result;
        try {
            result = bound.provider().write(new CalendarWrite(incoming, WriteIntent.CREATE, EventVersion.unknown()));
        } catch (CalendarAdapterException failure) {
            if (failure.type() != CalendarAdapterException.Type.CONFLICT) throw translate(failure);
            result = provider(() -> bound.provider().read(providerCalendar(member), scope, providerId));
            if (!CalendarUserModelMapper.content(incoming).equals(CalendarUserModelMapper.content(result))) throw conflict();
        }
        verifyEvent(member, scope, result, providerId);
        if (!CalendarUserModelMapper.content(incoming).equals(CalendarUserModelMapper.content(result))) throw unavailable();
        current(member, bound);
        return project(member, bound, scope, eventRef, result);
    }

    public Event update(Jwt jwt, String calendarRef, String eventRef, WriteRequest request, String ifMatch) {
        requireVersion(ifMatch);
        Member member = member(jwt, true);
        CalendarScope scope = scope(member, calendarRef, ContextPermission.EDIT);
        Bound bound = bound(member);
        CalendarEvent existing = readMapped(member, bound, scope, eventRef);
        if (!version(bound, eventRef, existing).equals(ifMatch)) throw stale();
        CalendarEvent incoming = content(member, scope, existing.id(), request);
        current(member, bound);
        audit(member, scope, eventRef, "update", ifMatch);
        CalendarEvent result = provider(() -> bound.provider().write(new CalendarWrite(incoming, WriteIntent.UPDATE, existing.version())));
        verifyEvent(member, scope, result, existing.id());
        if (!CalendarUserModelMapper.content(incoming).equals(CalendarUserModelMapper.content(result))) throw unavailable();
        current(member, bound);
        return project(member, bound, scope, eventRef, result);
    }

    public void delete(Jwt jwt, String calendarRef, String eventRef, String ifMatch) {
        requireVersion(ifMatch);
        Member member = member(jwt, true);
        CalendarScope scope = scope(member, calendarRef, ContextPermission.EDIT);
        Bound bound = bound(member);
        CalendarEvent existing = readMapped(member, bound, scope, eventRef);
        if (!version(bound, eventRef, existing).equals(ifMatch)) throw stale();
        current(member, bound);
        audit(member, scope, eventRef, "delete", ifMatch);
        provider(() -> { bound.provider().delete(providerCalendar(member), scope, existing.id(), existing.version()); return null; });
        current(member, bound);
    }

    private CalendarEvent content(Member member, CalendarScope scope, EventId providerId, WriteRequest request) {
        try { return CalendarUserModelMapper.event(providerCalendar(member), providerId, scope, request); }
        catch (IllegalArgumentException | CalendarAdapterException invalid) { throw invalid(); }
    }

    private Member member(Jwt jwt, boolean write) {
        if (!admission.allows(jwt)) throw error(HttpStatus.FORBIDDEN, "calendar-forbidden", "Calendar access is denied.");
        capabilities.requireCapability(jwt, write ? "calendar.manage_events" : "calendar.read", "calendar", write ? "write" : "read");
        String principal = context.principalRef(jwt.getClaimAsString(context.principalClaim()));
        if (principal == null) throw error(HttpStatus.UNAUTHORIZED, "unauthorized", "Member identity is required.");
        boolean mayEdit = write;
        if (!write) {
            try { capabilities.requireCapability(jwt, "calendar.manage_events", "calendar", "allowed-actions"); mayEdit = true; }
            catch (ApiErrorException denied) { mayEdit = false; }
        }
        return new Member(identities.resolve(jwt).organizationId(), principal, mayEdit);
    }

    private List<CalendarScope> scopes(String organization) {
        var result = new LinkedHashSet<CalendarScope>();
        result.add(CalendarScope.workspace());
        var edges = context.toGraphEdges().stream().filter(edge -> organization.equals(edge.tenantId())
                && edge.relation() == ContextGraphRelation.CONTAINS).toList();
        for (var edge : edges) {
            if ("workspace-default".equals(edge.fromContextId()) && edge.toContextId().matches("team-[A-Za-z0-9_-]{1,128}")) {
                String team = edge.toContextId().substring(5);
                result.add(new CalendarScope(ScopeType.TEAM, team, null));
                for (var child : edges) {
                    if (edge.toContextId().equals(child.fromContextId()) && child.toContextId().matches("channel-[A-Za-z0-9_-]{1,128}")) {
                        result.add(new CalendarScope(ScopeType.CHANNEL, team, child.toContextId().substring(8)));
                    }
                }
            }
        }
        return List.copyOf(result);
    }

    private CalendarScope scope(Member member, String calendar, ContextPermission permission) {
        CalendarScope scope = scopes(member.organization()).stream().filter(value -> calendarRef(member, value).equals(calendar))
                .findFirst().orElseThrow(this::missing);
        if (!allowed(member, scope, permission)) throw error(HttpStatus.FORBIDDEN, "calendar-forbidden", "Calendar access is denied for this Space.");
        return scope;
    }

    private boolean allowed(Member member, CalendarScope scope, ContextPermission permission) {
        return authorization.check(new ContextAuthorizationRequest(member.organization(), space(scope), member.principal(), permission)).allowed();
    }

    private List<String> actions(Member member, CalendarScope scope) {
        return member.mayEdit() && allowed(member, scope, ContextPermission.EDIT) ? List.of("read", "create", "update", "delete") : List.of("read");
    }

    private String space(CalendarScope scope) {
        return switch (scope.type()) { case WORKSPACE -> "workspace-default"; case TEAM -> "team-" + scope.teamId(); case CHANNEL -> "channel-" + scope.channelId(); };
    }

    private Scope transportScope(CalendarScope scope) { return new Scope(scope.type(), space(scope), scope.teamId(), scope.channelId()); }
    private String calendarRef(Member member, CalendarScope scope) { return "calendar:" + digest(member.organization() + "\0" + scope); }
    private CalendarId providerCalendar(Member member) { return new CalendarId("calendar-" + digest(member.organization()).substring(0, 40)); }
    private String providerRef(Member member, CalendarScope scope, EventId id) { return calendarRef(member, scope) + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(id.value().getBytes(StandardCharsets.UTF_8)); }

    private Bound bound(Member member) {
        ProviderBinding binding = bindings.current(member.organization(), DOMAIN)
                .filter(value -> value.state() == ProviderBinding.State.ACTIVE && member.organization().equals(value.organizationRef())
                        && DOMAIN.equals(value.domain()) && CONFIGURATION_REF.equals(value.configurationRef())).orElseThrow(this::unavailable);
        CalendarProviderPort provider = providers.get(binding.adapterKey());
        if (provider == null || !provider.configured()) throw unavailable();
        return new Bound(binding, provider);
    }

    private void current(Member member, Bound bound) {
        if (!bindings.current(member.organization(), DOMAIN).filter(bound.binding()::equals).isPresent()) throw unavailable();
    }

    private String identity(Member member, Bound bound, CalendarScope scope, EventId id, String requested) {
        String providerRef = providerRef(member, scope, id);
        var mapping = bindings.mappingByProviderRef(member.organization(), DOMAIN, bound.binding().revision(), providerRef);
        if (mapping.isPresent()) {
            ProviderObjectMapping existing = mapping.get();
            if (!member.organization().equals(existing.organizationRef()) || !DOMAIN.equals(existing.domain())
                    || bound.binding().revision() != existing.bindingRevision() || !providerRef.equals(existing.providerObjectRef())
                    || !existing.canonicalObjectId().matches("event:[0-9a-f]{64}")) throw unavailable();
            if (requested != null && !requested.equals(mapping.get().canonicalObjectId())) throw conflict();
            return mapping.get().canonicalObjectId();
        }
        String canonical = requested == null ? "event:" + digest(member.organization() + "\0" + providerRef) : requested;
        Instant now = Instant.now();
        return bindings.saveMapping(new ProviderObjectMapping(member.organization(), DOMAIN, bound.binding().revision(), canonical,
                providerRef, "calendar-user-api", now, now)).canonicalObjectId();
    }

    private CalendarEvent readMapped(Member member, Bound bound, CalendarScope scope, String id) {
        if (id == null || !id.matches("event:[0-9a-f]{64}")) throw missing();
        String ref = bindings.mappingByCanonicalId(member.organization(), DOMAIN, bound.binding().revision(), id)
                .filter(value -> member.organization().equals(value.organizationRef()) && DOMAIN.equals(value.domain())
                        && bound.binding().revision() == value.bindingRevision() && id.equals(value.canonicalObjectId()))
                .map(ProviderObjectMapping::providerObjectRef).orElseThrow(this::missing);
        String prefix = calendarRef(member, scope) + ".";
        if (!ref.startsWith(prefix) || ref.length() == prefix.length()) throw missing();
        EventId providerId;
        try { providerId = new EventId(new String(Base64.getUrlDecoder().decode(ref.substring(prefix.length())), StandardCharsets.UTF_8)); }
        catch (IllegalArgumentException malformed) { throw unavailable(); }
        CalendarEvent event = provider(() -> bound.provider().read(providerCalendar(member), scope, providerId));
        verifyEvent(member, scope, event, providerId);
        return event;
    }

    private void verifyEvent(Member member, CalendarScope scope, CalendarEvent event, EventId expected) {
        if (event == null || !providerCalendar(member).equals(event.calendarId()) || !scope.equals(event.scope())
                || expected != null && !expected.equals(event.id())) throw unavailable();
        try { CalendarUserModelMapper.requireLossless(event); }
        catch (IllegalArgumentException | CalendarAdapterException unsupported) { throw unavailable(); }
    }

    private Event project(Member member, Bound bound, CalendarScope scope, String id, CalendarEvent event) {
        return new Event(id, calendarRef(member, scope), transportScope(scope), "meeting:" + digest(member.organization() + "\0" + id),
                version(bound, id, event), actions(member, scope), CalendarUserModelMapper.content(event));
    }

    private String version(Bound bound, String id, CalendarEvent event) {
        String etag = event.version().value();
        if (etag == null || !etag.matches("\"[^\"\\r\\n]+\"")) throw unavailable();
        return "\"calendar-" + digest(id + "\0" + bound.binding().revision() + "\0" + etag) + "\"";
    }

    private void requireVersion(String value) {
        if (value == null || value.isBlank()) throw error(HttpStatus.PRECONDITION_REQUIRED, "calendar-precondition-required", "A current If-Match version is required.");
        if (!value.matches("\"calendar-[0-9a-f]{64}\"")) throw invalid();
    }

    private void audit(Member member, CalendarScope scope, String id, String action, String version) {
        try {
            AuditWriteGate.publishRequired(audit, new AuditEvent(member.organization(), space(scope), member.principal(), "weave:calendar-user-api",
                    AuditAction.CALENDAR_EVENT_WRITE_ATTEMPTED, Instant.now(),
                    "calendar-write:" + digest(id + "\0" + action + "\0" + version + "\0" + UUID.randomUUID()),
                    AuditRedactionLevel.SUPPORT_SAFE, Map.of("module", DOMAIN, "operation", action, "supportSafe", true)));
        } catch (RuntimeException unavailable) {
            throw error(HttpStatus.SERVICE_UNAVAILABLE, "calendar-audit-unavailable", "Calendar audit is unavailable.");
        }
    }

    private <T> T provider(Supplier<T> operation) {
        try { return operation.get(); } catch (CalendarAdapterException failure) { throw translate(failure); }
    }
    private ApiErrorException translate(CalendarAdapterException failure) {
        if ("calendar-window-too-large".equals(failure.details().get("errorCode"))) return tooLarge();
        return switch (failure.type()) { case NOT_FOUND -> missing(); case CONFLICT -> stale(); default -> unavailable(); };
    }
    private ApiErrorException missing() { return error(HttpStatus.NOT_FOUND, "calendar-event-not-found", "Calendar or event is unavailable."); }
    private ApiErrorException stale() { return error(HttpStatus.PRECONDITION_FAILED, "calendar-version-conflict", "The event version has changed."); }
    private ApiErrorException conflict() { return error(HttpStatus.CONFLICT, "calendar-create-conflict", "The create identity is already bound to another event payload."); }
    private ApiErrorException unavailable() { return error(HttpStatus.SERVICE_UNAVAILABLE, "calendar-provider-unavailable", "The Calendar provider or identity mapping is unavailable."); }
    private ApiErrorException invalid() { return error(HttpStatus.BAD_REQUEST, "calendar-invalid-request", "Calendar input is outside the supported profile."); }
    private ApiErrorException tooLarge() { return error(HttpStatus.UNPROCESSABLE_CONTENT, "calendar-window-too-large", "Use a smaller Calendar agenda window."); }
    private ApiErrorException error(HttpStatus status, String code, String message) { return new ApiErrorException(status, code, message, Map.of("module", DOMAIN, "diagnosticsRedacted", true)); }
    private static String digest(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private record Member(String organization, String principal, boolean mayEdit) {}
    private record Bound(ProviderBinding binding, CalendarProviderPort provider) {}
}
