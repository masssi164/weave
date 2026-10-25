package com.massimotter.weave.backend.calendar.api;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;

import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation;
import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Settings;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.api.error.ApiExceptionHandler;
import com.massimotter.weave.backend.calendar.api.CalendarUserModels.*;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.calendar.application.CalendarUserApiService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = CalendarUserController.class, excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({CalendarUserController.class, SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
class CalendarUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean CalendarUserApiService calendar;
    @MockitoBean JwtDecoder jwtDecoder;
    private static final String ROUTE = "/api/calendar/calendars/calendar:test/events";
    private static final String BODY = """
            {"title":"Planning","start":{"kind":"DATE","date":"2026-03-28"},
             "end":{"kind":"DATE","date":"2026-03-29"},"attendees":[],"overrides":[]}
            """;

    @Test
    void realGuestUserCalendarReadAndMutationStopAtCapabilityPolicyBeforeProviderAccess() throws Exception {
        var properties = new org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties();
        properties.getJwt().setIssuerUri("https://auth.example.invalid/realms/acme");
        var policy = WorkspaceCapabilityTestInstallation.service(properties,
                new WeaveSecurityProperties("weave-app", "weave-app"),
                new Settings(null, null, null, null, null, null));
        var guest = org.springframework.security.oauth2.jwt.Jwt.withTokenValue("guest-fixture")
                .header("alg", "none").subject("guest").claim("iss", "https://auth.example.invalid/realms/acme")
                .claim("aud", List.of("weave-app")).claim("weave_tenant_id", "tenant-default")
                .claim("organization", HumanJwtTestSupport.organizationWithRole("guest")).build();
        org.assertj.core.api.Assertions.assertThat(policy.effectivePolicySnapshot(guest, "workspace-default").orgRoles()).containsExactly("guest");
        org.assertj.core.api.Assertions.assertThat(policy.grantedCapabilities(guest)).isEmpty();
        var context = new ContextAuthorizationProperties(null, null, null, null, null, null, null, null);
        var rights = mock(com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort.class);
        var bindings = mock(com.massimotter.weave.backend.providerbinding.port.ProviderBindingRepository.class);
        var audit = mock(com.massimotter.weave.backend.audit.AuditEventPublisher.class);
        var realCalendar = new CalendarUserApiService(HumanJwtTestSupport.organizationAdmission(),
                com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver.configured(context),
                context, rights, policy, bindings, List.of(), audit);
        // Reuse the MVC/security fixture while obtaining the denial from real policy code.
        doAnswer(call -> realCalendar.read(call.getArgument(0), call.getArgument(1), call.getArgument(2)))
                .when(calendar).read(any(), eq("calendar:test"), eq("event:guest-probe"));
        doAnswer(call -> { realCalendar.delete(call.getArgument(0), call.getArgument(1), call.getArgument(2), call.getArgument(3)); return null; })
                .when(calendar).delete(any(), eq("calendar:test"), eq("event:guest-probe"), any());
        for (var denied : List.of(Map.entry(get(ROUTE + "/event:guest-probe"), "calendar.read"),
                Map.entry(delete(ROUTE + "/event:guest-probe").header("If-Match", "\"calendar-" + "a".repeat(64) + "\""), "calendar.manage_events"))) {
            mvc.perform(denied.getKey().with(jwt().jwt(guest).authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                    .andExpect(jsonPath("$.details.module").value("calendar"))
                    .andExpect(jsonPath("$.details.requiredCapability").value(denied.getValue()))
                    .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
        }
        verifyNoInteractions(rights, bindings, audit);
    }

    @Test
    void unauthenticatedAndForeignNativeOrganizationCannotReachCalendarService() throws Exception {
        mvc.perform(get("/api/calendar/calendars")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/calendar/calendars").with(jwt().jwt(token -> token.claim("organization", Map.of("foreign", Map.of("id", "foreign"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(calendar);
    }

    @Test
    void createHasJsonContractAndStrongResponseVersion() throws Exception {
        WriteRequest content = new WriteRequest("Planning", null, new TimeValue(TemporalKind.DATE, "2026-03-28", null, null, null),
                new TimeValue(TemporalKind.DATE, "2026-03-29", null, null, null), null, List.of(), null, List.of());
        when(calendar.create(any(), eq("calendar:test"), any(), eq("calendar-create-key-1"))).thenReturn(
                new Event("event:test", "calendar:test", new Scope(ScopeType.WORKSPACE, "workspace-default", null, null),
                        "meeting:test", "\"calendar-test\"", List.of("read", "update", "delete"), content));
        mvc.perform(post(ROUTE).with(member()).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "calendar-create-key-1").content(BODY))
                .andExpect(status().isCreated()).andExpect(header().string("ETag", "\"calendar-test\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.content.start.kind").value("DATE"))
                .andExpect(jsonPath("$.content.start.date").value("2026-03-28"))
                .andExpect(jsonPath("$.providerRef").doesNotExist());
    }

    @Test
    void previewReadExposesOnlyTransientContentAndMaterializationReturnsStableVersion() throws Exception {
        String handle = "pv_0123456789abcdefghijklmnopqrstuv";
        WriteRequest content = new WriteRequest("Planning", null,
                new TimeValue(TemporalKind.DATE, "2026-03-28", null, null, null),
                new TimeValue(TemporalKind.DATE, "2026-03-29", null, null, null),
                null, List.of(), null, List.of());
        Scope scope = new Scope(ScopeType.WORKSPACE, "workspace-default", null, null);
        when(calendar.readPreview(any(), eq("calendar:test"), eq(handle))).thenReturn(
                new EventPreview(handle, "calendar:test", scope, Instant.parse("2026-03-28T12:00:00Z"),
                        List.of("read", "materialize", "update"), content));
        when(calendar.materializePreview(any(), eq("calendar:test"), eq(handle))).thenReturn(
                new Event("event:test", "calendar:test", scope, "meeting:test", "\"calendar-test\"",
                        List.of("read", "update"), content));
        String route = ROUTE + "/previews/" + handle;
        mvc.perform(get(route).with(member())).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.handle").value(handle))
                .andExpect(jsonPath("$.content.title").value("Planning"))
                .andExpect(jsonPath("$.id").doesNotExist())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.meetingThreadRef").doesNotExist());
        mvc.perform(post(route + "/materialization").with(member()))
                .andExpect(status().isOk())
                .andExpect(header().string("ETag", "\"calendar-test\""))
                .andExpect(jsonPath("$.id").value("event:test"))
                .andExpect(jsonPath("$.meetingThreadRef").value("meeting:test"));
        mvc.perform(post(route + "/materialization"))
                .andExpect(status().isUnauthorized());
        verify(calendar, times(1)).materializePreview(any(), eq("calendar:test"), eq(handle));
    }

    @Test
    void unknownContentAndTemporalFieldsAreRejectedWithoutCallingService() throws Exception {
        for (String body : List.of(BODY.replace("\"title\":", "\"providerUid\":\"injected\",\"title\":"),
                BODY.replace("\"kind\":\"DATE\"", "\"kind\":\"DATE\",\"providerTimezone\":\"UTC\""))) {
            mvc.perform(post(ROUTE).with(member()).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "calendar-create-key-1").content(body))
                    .andExpect(status().isBadRequest());
        }
        verifyNoInteractions(calendar);
    }

    @Test
    void omittedRecurrenceIntervalIsRejectedBeforeAnyProviderWork() throws Exception {
        String recurrence = """
                "recurrence":{"frequency":"DAILY","count":3,"additionalDates":[],
                "excludedDates":[],"byDay":[],"byMonthDay":[],"byMonth":[],"bySetPos":[]},
                "overrides":[]
                """;
        mvc.perform(post(ROUTE).with(member()).contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "calendar-create-key-1")
                        .content(BODY.replace("\"overrides\":[]", recurrence)))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(calendar);
    }

    @Test
    void malformedAgendaTimeAndNullCollectionMembersAreClientErrors() throws Exception {
        mvc.perform(get(ROUTE).with(member()).param("from", "not-an-instant").param("to", "2026-03-29T00:00:00Z")
                        .param("evaluationTimeZone", "Europe/Berlin"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("calendar-invalid-query"));
        mvc.perform(post(ROUTE).with(member()).contentType(MediaType.APPLICATION_JSON)
                        .content(BODY.replace("\"attendees\":[]", "\"attendees\":[null]")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(calendar);
    }

    @Test
    void writesRejectUnsupportedMediaTypeWithTheStableErrorEnvelope() throws Exception {
        for (var request : List.of(post(ROUTE), put(ROUTE + "/event:test"))) {
            mvc.perform(request.with(member()).contentType(MediaType.TEXT_PLAIN).content(BODY))
                    .andExpect(status().isUnsupportedMediaType())
                    .andExpect(jsonPath("$.code").value("unsupported-media-type"))
                    .andExpect(jsonPath("$.supportRef").exists());
        }
        verifyNoInteractions(calendar);
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor member() {
        return jwt().jwt(token -> token.subject("member").claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
