package com.massimotter.weave.backend.service.calendar;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
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
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
