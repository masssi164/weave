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
        assertThatThrownBy(() -> service.update(member, calendar, created.id(), content("Updated"), created.version())).isInstanceOf(RuntimeException.class);
        verify(provider, never()).write(any());
        verify(provider, never()).delete(any(), any(), any(), any());
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
