package com.massimotter.weave.backend.service.calendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.agentruntime.adapter.McpExchangedTokenPolicy;
import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.domain.ExchangedWorkloadToken;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.WeaverWorkloadPrincipal;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.*;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import com.massimotter.weave.backend.portability.ProviderConformanceProfile;
import com.massimotter.weave.backend.providerbinding.domain.*;
import com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository;
import com.massimotter.weave.backend.service.*;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

class CalendarUserApiServiceTest {
    final CalendarProviderPort provider = mock(CalendarProviderPort.class);
    final ProviderBindingRepository bindings = mock(ProviderBindingRepository.class);
    final ContextAuthorizationPort rights = mock(ContextAuthorizationPort.class);
    final WorkspaceCapabilityService capabilities = mock(WorkspaceCapabilityService.class);
    final AuditEventPublisher audit = mock(AuditEventPublisher.class);
    final Map<String, ProviderObjectMapping> mappings = new HashMap<>();
    final Map<String, CalendarEvent> events = new HashMap<>();
    CalendarUserApiService service;
    final Jwt member = Jwt.withTokenValue("member").header("alg", "none").subject("member")
            .issuer("https://auth.weave.test/realms/weave").claim("organization", HumanJwtTestSupport.organizationWithRole("member")).build();
    String calendar;
    ProviderBinding binding;

    @BeforeEach
    void setup() {
        var context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        binding = new ProviderBinding("tenant-default", "calendar", 1, "test-provider", CalendarUserApiService.CONFIGURATION_REF,
                ProviderBinding.State.ACTIVE, Instant.EPOCH);
        when(bindings.current("tenant-default", "calendar")).thenReturn(Optional.of(binding));
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile("calendar", "test-provider", Set.of(), Map.of(), true, true, true));
        when(provider.configured()).thenReturn(true);
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.allow("test"));
        when(bindings.mappingByCanonicalId(anyString(), eq("calendar"), anyLong(), anyString())).thenAnswer(call -> Optional.ofNullable(mappings.get(call.getArgument(3))));
        when(bindings.mappingByProviderRef(anyString(), eq("calendar"), anyLong(), anyString())).thenAnswer(call -> mappings.values().stream().filter(m -> m.providerObjectRef().equals(call.getArgument(3))).findFirst());
        when(bindings.saveMapping(any())).thenAnswer(call -> { ProviderObjectMapping mapping = call.getArgument(0); mappings.put(mapping.canonicalObjectId(), mapping); return mapping; });
        when(provider.write(any())).thenAnswer(call -> {
            CalendarWrite write = call.getArgument(0); CalendarEvent e = write.event(); CalendarEvent previous = events.get(e.id().value());
            if (write.intent() == WriteIntent.CREATE && previous != null) throw new CalendarAdapterException(CalendarAdapterException.Type.CONFLICT, "private error");
            if (write.intent() == WriteIntent.UPDATE && !previous.version().equals(write.expectedVersion())) throw new CalendarAdapterException(CalendarAdapterException.Type.CONFLICT, "private error");
            CalendarEvent result = new CalendarEvent(e.calendarId(), e.id(), e.scope(), e.title(), e.description(), e.startValue(), e.endValue(), e.location(), e.attendees(), e.recurrence(), e.overrides(),
                    new EventVersion(previous == null ? "\"private-etag-1\"" : "\"private-etag-2\""), Instant.now());
            events.put(e.id().value(), result); return result;
        });
        when(provider.read(any(), any(), any())).thenAnswer(call -> events.get(((EventId) call.getArgument(2)).value()));
        service = new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(), OrganizationIdentityContextResolver.configured(context), context,
                rights, capabilities, bindings, List.of(provider), audit);
        calendar = service.calendars(member).calendars().getFirst().id();
        clearInvocations(provider, bindings, audit);
    }

    @Test
    void durableSpaceRevocationHidesCalendarsAndBlocksProviderQueryDespiteStaticGrant() {
        SpaceAccessPort spaces = mock(SpaceAccessPort.class);
        when(spaces.allows(eq("tenant-default"), eq("workspace-default"), anyString(),
                eq(SpaceAccessPort.Permission.VIEW))).thenReturn(true, false);
        var context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        service = new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                OrganizationIdentityContextResolver.configured(context), context, rights, spaces,
                capabilities, bindings, List.of(provider), audit);
        clearInvocations(rights);

        assertThat(service.calendars(member).calendars()).hasSize(1);
        assertThat(service.calendars(member).calendars()).isEmpty();
        assertStatus(() -> service.agenda(member, calendar, Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2026-01-02T00:00:00Z"), "UTC"), HttpStatus.FORBIDDEN);
        verify(provider, never()).query(any(), any(), any(), any());
        verify(rights, never()).check(any());
    }

    @Test
    void currentCalendarWorkloadReadsThroughMemberSpaceAndAuditsWithoutMaterializing() {
        CalendarUserApiService workload = workloadService();
        when(provider.query(any(), any(), any(), any())).thenReturn(List.of());

        assertThat(workload.calendars(workloadJwt()).calendars())
                .singleElement().satisfies(visible -> {
                    assertThat(visible.id()).isEqualTo(calendar);
                    assertThat(visible.allowedActions()).containsExactly("read");
                });
        assertThat(workload.agenda(workloadJwt(), calendar,
                Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"), "UTC")
                .events()).isEmpty();
        verify(provider).query(any(), any(), any(), any());
        verify(bindings, never()).saveMapping(any());
        verify(audit, times(2)).publish(any());
    }

    @Test
    void revokedWorkloadSpaceFailsBeforeCalendarProviderQuery() {
        CalendarUserApiService workload = workloadService();
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.deny("revoked"));

        assertStatus(() -> workload.agenda(workloadJwt(), calendar,
                Instant.parse("2026-10-07T00:00:00Z"), Instant.parse("2026-10-08T00:00:00Z"), "UTC"),
                HttpStatus.FORBIDDEN);
        verify(provider, never()).query(any(), any(), any(), any());
    }

    @Test
    void workloadCannotMaterializePreviewOrCreateAnEvent() {
        CalendarUserApiService workload = workloadService();
        assertStatus(() -> workload.create(workloadJwt(), calendar, content("Denied"),
                "workload-create-key"), HttpStatus.FORBIDDEN);
        verify(provider, never()).write(any());
        verify(bindings, never()).saveMapping(any());
    }

    @Test
    void authorizedCalendarWriteWorkloadUsesTheSameVersionedProviderMutationPath() {
        CalendarUserApiService workload = workloadService("calendar.write");
        Event created = workload.create(workloadJwt(), calendar, content("MCP planning"),
                "mcp-calendar-create-key");
        assertThat(service.read(member, calendar, created.id()).content().title())
                .isEqualTo("MCP planning");
        assertThat(workload.create(workloadJwt(), calendar, content("MCP planning"),
                "mcp-calendar-create-key")).isEqualTo(created);

        Event updated = workload.update(workloadJwt(), calendar, created.id(),
                content("MCP updated"), created.version());
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(service.read(member, calendar, created.id()).content().title())
                .isEqualTo("MCP updated");
        assertStatus(() -> workload.delete(workloadJwt(), calendar, created.id(),
                created.version()), HttpStatus.PRECONDITION_FAILED);
        workload.delete(workloadJwt(), calendar, created.id(), updated.version());
        verify(provider).delete(any(), any(), any(), any());
    }

    @Test
    void workloadPreviewRemainsTransientEvenWhenExplicitMaterializationIsRequested() {
        service.create(member, calendar, content("External planning"), "calendar-fixture-key-2");
        mappings.clear();
        clearInvocations(bindings, audit);
        when(provider.query(any(), any(), any(), any())).thenAnswer(call -> List.copyOf(events.values()));
        CalendarUserApiService workload = workloadService();
        EventPreview preview = workload.agenda(workloadJwt(), calendar,
                Instant.parse("2026-03-28T00:00:00Z"),
                Instant.parse("2026-03-29T00:00:00Z"), "UTC").previews().getFirst();
        clearInvocations(provider);

        assertStatus(() -> workload.materializePreview(workloadJwt(), calendar, preview.handle()),
                HttpStatus.FORBIDDEN);
        assertThat(mappings).isEmpty();
        verify(bindings, never()).saveMapping(any());
        verifyNoInteractions(provider);
    }

    @SuppressWarnings("unchecked")
    private CalendarUserApiService workloadService() {
        return workloadService("calendar.read");
    }

    @SuppressWarnings("unchecked")
    private CalendarUserApiService workloadService(String scope) {
        var authorization = mock(McpWorkloadAuthorizationService.class);
        var tokenPolicy = mock(McpExchangedTokenPolicy.class);
        ObjectProvider<McpWorkloadAuthorizationService> authorizationProvider = mock(ObjectProvider.class);
        ObjectProvider<McpExchangedTokenPolicy> tokenProvider = mock(ObjectProvider.class);
        when(authorizationProvider.getIfAvailable()).thenReturn(authorization);
        when(tokenProvider.getIfAvailable()).thenReturn(tokenPolicy);
        Instant now = Instant.now();
        var exchanged = new ExchangedWorkloadToken(
                "https://auth.weave.test/realms/weave", "workload-subject", "weave-mcp-server",
                Set.of(scope), now, now.plusSeconds(60), "exchange-calendar-1");
        var principal = new WeaverWorkloadPrincipal(
                exchanged.issuer(), exchanged.subject(), "weaver-cell-1", "weave-mcp-server",
                "tenant-default", "person-1", new RuntimeMemberBinding(exchanged.issuer(), "member"),
                "member", "cell-1", "profile-1", "sha256:profile", "entitlement-1",
                now.plusSeconds(60), Set.of(scope), Set.of(scope));
        when(tokenPolicy.resolve(any())).thenReturn(exchanged);
        when(authorization.authorize(exchanged)).thenReturn(principal);
        var context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        return new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                OrganizationIdentityContextResolver.configured(context), context, rights, null,
                capabilities, bindings, List.of(provider), audit, authorizationProvider, tokenProvider);
    }

    private Jwt workloadJwt() {
        return Jwt.withTokenValue("workload-token").header("typ", "at+jwt")
                .issuer("https://auth.weave.test/realms/weave").subject("workload-subject")
                .claim("azp", "weave-mcp-server").build();
    }

    @Test
    void httpGeneratedUtcSerializationRoundTripsAndNonzeroFractionsCannotWrite() throws Exception {
        var errors = new com.massimotter.weave.backend.config.ApiErrorResponseWriter(tools.jackson.databind.json.JsonMapper.builder().build());
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(
                new com.massimotter.weave.backend.controller.CalendarUserController(service, errors))
                .setControllerAdvice(new com.massimotter.weave.backend.exception.ApiExceptionHandler(errors))
                .setCustomArgumentResolvers(new org.springframework.web.method.support.HandlerMethodArgumentResolver() {
                    @java.lang.Override public boolean supportsParameter(org.springframework.core.MethodParameter parameter) { return parameter.getParameterType() == Jwt.class; }
                    @java.lang.Override public Object resolveArgument(org.springframework.core.MethodParameter parameter,
                            org.springframework.web.method.support.ModelAndViewContainer container,
                            org.springframework.web.context.request.NativeWebRequest request,
                            org.springframework.web.bind.support.WebDataBinderFactory binder) { return member; }
                }).build();
        String body = """
                {"title":"Generated UTC","start":{"kind":"UTC","instant":"2026-10-25T09:00:00.000Z"},
                 "end":{"kind":"UTC","instant":"2026-10-25T10:00:00.000Z"},"attendees":[],"overrides":[]}
                """;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calendar/calendars/{id}/events", calendar)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Idempotency-Key", "calendar-http-key-1").content(body))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content.start.instant").value("2026-10-25T09:00:00Z"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content.end.instant").value("2026-10-25T10:00:00Z"));
        clearInvocations(provider, audit);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/calendar/calendars/{id}/events", calendar)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON).header("Idempotency-Key", "calendar-http-key-2")
                        .content(body.replace(".000Z", ".001Z")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code").value("calendar-invalid-request"));
        verify(provider, never()).write(any());
        verifyNoInteractions(audit);
    }

    @Test
    void stableIdentityRetryReadUpdateAndDeleteUsePrivateVersionsAndPreserveTemporalPayload() {
        WriteRequest first = content("Planning");
        Event created = service.create(member, calendar, first, "calendar-create-key-1");
        assertThat(service.create(member, calendar, first, "calendar-create-key-1")).isEqualTo(created);
        assertThat(service.read(member, calendar, created.id()).content()).isEqualTo(first);
        assertThat(created.toString()).doesNotContain("private-etag", "@calendar", "configuration:");
        assertStatus(() -> service.update(member, calendar, created.id(), content("Updated"), null), HttpStatus.PRECONDITION_REQUIRED);
        Event updated = service.update(member, calendar, created.id(), content("Updated"), created.version());
        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.meetingThreadRef()).isEqualTo(created.meetingThreadRef());
        assertStatus(() -> service.update(member, calendar, created.id(), content("Updated"), created.version()), HttpStatus.PRECONDITION_FAILED);
        assertStatus(() -> service.delete(member, calendar, created.id(), created.version()), HttpStatus.PRECONDITION_FAILED);
        service.delete(member, calendar, created.id(), updated.version());
        verify(provider).delete(any(), eq(CalendarScope.workspace()), any(), eq(new EventVersion("\"private-etag-2\"")));
        assertThat(mappings.values()).allSatisfy(mapping -> assertThat(mapping.providerObjectRef()).doesNotContain("\0"));
    }

    @Test
    void spaceEventCandidatesComeOnlyFromConfirmedMappingsAndCurrentScopeRights() {
        Event created = service.create(member, calendar, content("Planning"), "calendar-create-key-relations");
        when(bindings.mappedByProviderRefPrefix(eq("tenant-default"), eq("calendar"), eq(1L),
                anyString(), eq(""), eq(10))).thenAnswer(call -> mappings.values().stream()
                        .filter(mapping -> mapping.providerObjectRef().startsWith(call.getArgument(3)))
                        .toList());
        clearInvocations(provider);
        assertThat(service.materializedEventRefsInSpace(member, "workspace-default", "", 10))
                .containsExactly(created.id());
        verify(provider, never()).query(any(), any(), any(), any());
        verify(provider, never()).read(any(), any(), any());
        assertThat(service.materializedEventRefsInSpace(member, "team-missing", "", 10)).isEmpty();
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.deny("revoked"));
        assertThat(service.materializedEventRefsInSpace(member, "workspace-default", "", 10)).isEmpty();
    }

    @Test
    void replayAuditsEachWriteAttemptWithItsOwnDurableIdempotencyKey() {
        WriteRequest input = content("Planning");
        Event created = service.create(member, calendar, input, "calendar-create-key-1");
        assertThat(service.create(member, calendar, input, "calendar-create-key-1")).isEqualTo(created);

        ArgumentCaptor<AuditEvent> attempts = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(2)).publish(attempts.capture());
        assertThat(attempts.getAllValues()).extracting(AuditEvent::idempotencyKey).doesNotHaveDuplicates();
        assertThat(attempts.getAllValues()).allSatisfy(event -> {
            assertThat(event.action()).isEqualTo(com.massimotter.weave.backend.audit.AuditAction.CALENDAR_EVENT_WRITE_ATTEMPTED);
            assertThat(event.tenantId()).isEqualTo("tenant-default");
        });
    }

    @Test
    void unknownScopeForeignOrganizationAndDeniedSpaceNeverReachProvider() {
        assertStatus(() -> service.create(member, "calendar:unknown", content("Planning"), "calendar-create-key-1"), HttpStatus.NOT_FOUND);
        Jwt foreign = Jwt.withTokenValue("foreign").header("alg", "none").subject("foreign")
                .claim("organization", Map.of("foreign", Map.of("id", "foreign"))).build();
        assertStatus(() -> service.calendars(foreign), HttpStatus.FORBIDDEN);
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.deny("not-member"));
        assertStatus(() -> service.create(member, calendar, content("Planning"), "calendar-create-key-1"), HttpStatus.FORBIDDEN);
        verifyNoInteractions(provider, audit);
    }

    @Test
    void auditFailureAndDifferentCreatePayloadCannotWrite() {
        Event created = service.create(member, calendar, content("Planning"), "calendar-create-key-1");
        assertStatus(() -> service.create(member, calendar, content("Different"), "calendar-create-key-1"), HttpStatus.CONFLICT);
        clearInvocations(provider);
        doThrow(new IllegalStateException("audit unavailable")).when(audit).publish(any());
        assertStatus(() -> service.update(member, calendar, created.id(), content("Updated"), created.version()), HttpStatus.SERVICE_UNAVAILABLE);
        verify(provider, never()).write(any());
        verify(provider, never()).delete(any(), any(), any(), any());
    }

    @Test
    void rejectedCreateDoesNotMaterializeAResource() {
        doThrow(new IllegalStateException("audit unavailable")).when(audit).publish(any());
        assertStatus(() -> service.create(member, calendar, content("Planning"), "calendar-create-key-1"),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mappings).isEmpty();
        verify(bindings, never()).saveMapping(any());
        verify(provider, never()).write(any());
    }

    @Test
    void failedProviderCreateDoesNotMaterializeAResourceAndCanRetry() {
        doThrow(new CalendarAdapterException(
                CalendarAdapterException.Type.DOWNSTREAM_UNAVAILABLE, "private failure"))
                .when(provider).write(any());
        assertStatus(() -> service.create(member, calendar, content("Planning"), "calendar-create-key-1"),
                HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mappings).isEmpty();
        verify(bindings, never()).saveMapping(any());

        reset(provider);
        when(provider.conformanceProfile()).thenReturn(new ProviderConformanceProfile(
                "calendar", "test-provider", Set.of(), Map.of(), true, true, true));
        when(provider.configured()).thenReturn(true);
        when(provider.write(any())).thenAnswer(call -> {
            CalendarEvent input = ((CalendarWrite) call.getArgument(0)).event();
            return new CalendarEvent(input.calendarId(), input.id(), input.scope(), input.title(), input.description(),
                    input.startValue(), input.endValue(), input.location(), input.attendees(), input.recurrence(),
                    input.overrides(), new EventVersion("\"private-etag-1\""), Instant.now());
        });
        Event recovered = service.create(member, calendar, content("Planning"), "calendar-create-key-1");
        assertThat(recovered.id()).startsWith("event:");
        assertThat(mappings).containsKey(recovered.id());
        verify(bindings, times(1)).saveMapping(any());
    }

    @Test
    void unsupportedExistingAttendeeIdentityBlocksReplacementBeforeAnyProviderWrite() {
        Event created = service.create(member, calendar, content("Planning"), "calendar-create-key-1");
        CalendarEvent existing = events.values().iterator().next();
        events.put(existing.id().value(), new CalendarEvent(existing.calendarId(), existing.id(), existing.scope(), existing.title(),
                existing.description(), existing.startValue(), existing.endValue(), existing.location(),
                List.of(new com.massimotter.weave.backend.calendar.domain.CalendarDomain.Attendee("member:private", "Member", "member@example.test", null, null)),
                existing.recurrence(), existing.overrides(), existing.version(), existing.updatedAt()));
        clearInvocations(provider, audit);
        assertStatus(() -> service.update(member, calendar, created.id(), content("Updated"), created.version()), HttpStatus.SERVICE_UNAVAILABLE);
        verify(provider, never()).write(any());
        verifyNoInteractions(audit);
    }

    @Test
    void extremeAgendaWindowsAreRejectedBeforeProviderAccess() {
        assertStatus(() -> service.agenda(member, calendar, Instant.MIN, Instant.MIN.plusSeconds(3600), "UTC"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service.agenda(member, calendar, Instant.MAX.minusSeconds(3600), Instant.MAX, "UTC"), HttpStatus.BAD_REQUEST);
        verifyNoInteractions(provider, audit);
    }

    @Test
    void browsingProviderEventUsesTransientPreviewUntilExplicitMaterialization() {
        service.create(member, calendar, content("External planning"), "calendar-fixture-key-1");
        mappings.clear();
        clearInvocations(bindings, audit);
        when(provider.query(any(), any(), any(), any())).thenAnswer(call -> List.copyOf(events.values()));

        Agenda agenda = service.agenda(member, calendar, Instant.parse("2026-03-28T00:00:00Z"),
                Instant.parse("2026-03-29T00:00:00Z"), "UTC");
        assertThat(agenda.events()).isEmpty();
        assertThat(agenda.occurrences()).isEmpty();
        assertThat(agenda.previews()).hasSize(1);
        assertThat(agenda.previewOccurrences()).hasSize(1);
        EventPreview preview = agenda.previews().getFirst();
        assertThat(preview.handle()).startsWith("pv_").doesNotContain("@calendar", "event:");
        assertThat(preview.content().title()).isEqualTo("External planning");
        assertThat(agenda.previewOccurrences().getFirst().previewHandle()).isEqualTo(preview.handle());
        assertThat(service.readPreview(member, calendar, preview.handle()).content()).isEqualTo(preview.content());
        assertThat(mappings).isEmpty();
        verify(bindings, never()).saveMapping(any());

        Event materialized = service.materializePreview(member, calendar, preview.handle());
        assertThat(materialized.id()).startsWith("event:");
        assertThat(service.materializePreview(member, calendar, preview.handle()).id()).isEqualTo(materialized.id());
        assertThat(mappings).containsOnlyKeys(materialized.id());
        ArgumentCaptor<AuditEvent> materializations = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, times(2)).publish(materializations.capture());
        assertThat(materializations.getAllValues()).allSatisfy(attempt -> {
            assertThat(attempt.action()).isEqualTo(com.massimotter.weave.backend.audit.AuditAction.CALENDAR_EVENT_REFERENCE_MATERIALIZATION_ATTEMPTED);
            assertThat(attempt.tenantId()).isEqualTo("tenant-default");
        });
        assertThat(service.agenda(member, calendar, Instant.parse("2026-03-28T00:00:00Z"),
                Instant.parse("2026-03-29T00:00:00Z"), "UTC").events())
                .extracting(Event::id).containsExactly(materialized.id());
    }

    @Test
    void previewCannotCrossActorBindingVersionOrAuditBoundary() {
        service.create(member, calendar, content("External planning"), "calendar-fixture-key-1");
        mappings.clear();
        when(provider.query(any(), any(), any(), any())).thenAnswer(call -> List.copyOf(events.values()));
        EventPreview preview = service.agenda(member, calendar, Instant.parse("2026-03-28T00:00:00Z"),
                Instant.parse("2026-03-29T00:00:00Z"), "UTC").previews().getFirst();
        Jwt other = Jwt.withTokenValue("other").header("alg", "none").subject("other")
                .issuer("https://auth.weave.test/realms/weave")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")).build();
        assertStatus(() -> service.materializePreview(other, calendar, preview.handle()), HttpStatus.NOT_FOUND);
        assertStatus(() -> service.materializePreview(member, calendar, preview.handle() + "x"), HttpStatus.NOT_FOUND);
        assertThat(mappings).isEmpty();

        clearInvocations(provider, audit);
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.deny("revoked"));
        assertStatus(() -> service.readPreview(member, calendar, preview.handle()), HttpStatus.FORBIDDEN);
        assertStatus(() -> service.materializePreview(member, calendar, preview.handle()), HttpStatus.FORBIDDEN);
        assertThat(mappings).isEmpty();
        verify(provider, never()).read(any(), any(), any());
        verifyNoInteractions(audit);
        when(rights.check(any())).thenReturn(ContextAuthorizationDecision.allow("restored"));

        doThrow(new IllegalStateException("audit unavailable")).when(audit).publish(any());
        assertStatus(() -> service.materializePreview(member, calendar, preview.handle()), HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(mappings).isEmpty();
        reset(audit);

        CalendarEvent original = events.values().iterator().next();
        events.put(original.id().value(), new CalendarEvent(original.calendarId(), original.id(), original.scope(),
                original.title(), original.description(), original.startValue(), original.endValue(), original.location(),
                original.attendees(), original.recurrence(), original.overrides(), new EventVersion("\"private-etag-2\""), Instant.now()));
        assertStatus(() -> service.materializePreview(member, calendar, preview.handle()), HttpStatus.PRECONDITION_FAILED);
        assertThat(mappings).isEmpty();
        events.put(original.id().value(), original);

        ProviderBinding newer = new ProviderBinding("tenant-default", "calendar", 2, "test-provider",
                CalendarUserApiService.CONFIGURATION_REF, ProviderBinding.State.ACTIVE, Instant.now());
        when(bindings.current("tenant-default", "calendar")).thenReturn(Optional.of(newer));
        assertStatus(() -> service.materializePreview(member, calendar, preview.handle()), HttpStatus.PRECONDITION_FAILED);
        assertThat(mappings).isEmpty();
    }

    @Test
    void missingOrDifferentBindingFailsClosed() {
        when(bindings.current("tenant-default", "calendar")).thenReturn(Optional.empty());
        assertStatus(() -> service.create(member, calendar, content("Planning"), "calendar-create-key-1"), HttpStatus.SERVICE_UNAVAILABLE);
        verifyNoInteractions(provider, audit);
    }

    static WriteRequest content(String title) {
        return new WriteRequest(title, null,
                CalendarUserModelMapperTest.time(TemporalKind.FLOATING, "2026-03-28", "09:00:00"),
                CalendarUserModelMapperTest.time(TemporalKind.FLOATING, "2026-03-28", "10:00:00"), null, List.of(), null, List.of());
    }
    static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiErrorException.class, error -> assertThat(error.status()).isEqualTo(status));
    }
}
