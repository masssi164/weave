package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.*;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
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
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
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
    void unknownContentAndTemporalFieldsAreRejectedWithoutCallingService() throws Exception {
        for (String body : List.of(BODY.replace("\"title\":", "\"providerUid\":\"injected\",\"title\":"),
                BODY.replace("\"kind\":\"DATE\"", "\"kind\":\"DATE\",\"providerTimezone\":\"UTC\""))) {
            mvc.perform(post(ROUTE).with(member()).contentType(MediaType.APPLICATION_JSON).header("Idempotency-Key", "calendar-create-key-1").content(body))
                    .andExpect(status().isBadRequest());
        }
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
