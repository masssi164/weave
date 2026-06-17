package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {FilesController.class, CalendarController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        FilesFacadeService.class,
        CalendarFacadeService.class,
        WorkspaceCapabilityService.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
        WeaverRuntimeProperties.class,
        WorkspaceCapabilityProperties.class,
        OAuth2ResourceServerProperties.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.security.client-id=weave-app",
        "weave.security.required-audience=weave-app"
})
class FilesCalendarFacadeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @MockBean
    private ContextAuthorizationProperties contextAuthorizationProperties;

    @BeforeEach
    void allowContextAccess() {
        when(contextAuthorizationPort.check(any()))
                .thenReturn(ContextAuthorizationDecision.allow("test allow"));
        when(contextAuthorizationProperties.tenantClaim()).thenReturn("weave_tenant_id");
        when(contextAuthorizationProperties.tenantFallbackClaim()).thenReturn("tenant_id");
        when(contextAuthorizationProperties.defaultTenantId()).thenReturn("tenant-default");
        when(contextAuthorizationProperties.principalClaim()).thenReturn("sub");
        when(contextAuthorizationProperties.principalRefPrefix()).thenReturn("user:");
        when(contextAuthorizationProperties.principalRef(any())).thenAnswer(invocation -> "user:" + invocation.getArgument(0));
    }

    @Test
    void filesFacadeRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/files"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void filesFacadeExposesStableUnavailableErrorUntilNextcloudAdapterExists() throws Exception {
        mockMvc.perform(get("/api/files")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("files-adapter-not-configured"))
                .andExpect(jsonPath("$.message").value(
                        "Files capability is unavailable until an admin completes workspace storage configuration."))
                .andExpect(jsonPath("$.details.module").value("files"))
                .andExpect(jsonPath("$.details.operation").value("list-files"));
    }

    @Test
    void filesFacadeFailsClosedWhenContextAuthorizationDeniesAccess() throws Exception {
        when(contextAuthorizationPort.check(any()))
                .thenReturn(ContextAuthorizationDecision.deny("no matching context membership"));

        mockMvc.perform(get("/api/files")
                        .with(workspaceJwt()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("files-forbidden"))
                .andExpect(jsonPath("$.details.module").value("files"))
                .andExpect(jsonPath("$.details.operation").value("list-files"))
                .andExpect(jsonPath("$.details.contextId").value("workspace-default"))
                .andExpect(jsonPath("$.details.permission").value("view"));
    }

    @Test
    void calendarFacadeRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/calendar/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void calendarFacadeExposesStableUnavailableErrorUntilNextcloudAdapterExists() throws Exception {
        mockMvc.perform(get("/api/calendar/events")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("calendar-adapter-not-configured"))
                .andExpect(jsonPath("$.message").value(
                        "Calendar facade is available, but calendar storage is not configured yet."))
                .andExpect(jsonPath("$.details.module").value("calendar"))
                .andExpect(jsonPath("$.details.operation").value("list-events"));
    }

    @Test
    void calendarScopesExposeWorkspaceTeamAndChannelContextMetadata() throws Exception {
        mockMvc.perform(get("/api/calendar/scopes")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scopes[0].id").value("workspace"))
                .andExpect(jsonPath("$.scopes[0].type").value("workspace"))
                .andExpect(jsonPath("$.scopes[0].contextId").value("workspace-default"))
                .andExpect(jsonPath("$.scopes[0].accessModel").value("shared-workspace-calendar"))
                .andExpect(jsonPath("$.scopes[1].id").value("team:engineering"))
                .andExpect(jsonPath("$.scopes[1].type").value("team"))
                .andExpect(jsonPath("$.scopes[1].teamId").value("engineering"))
                .andExpect(jsonPath("$.scopes[1].contextId").value("team-engineering"))
                .andExpect(jsonPath("$.scopes[1].accessModel").value("shared-team-calendar"))
                .andExpect(jsonPath("$.scopes[2].id").value("channel:engineering-general"))
                .andExpect(jsonPath("$.scopes[2].type").value("channel"))
                .andExpect(jsonPath("$.scopes[2].teamId").value("engineering"))
                .andExpect(jsonPath("$.scopes[2].channelId").value("engineering-general"))
                .andExpect(jsonPath("$.scopes[2].contextId").value("channel-engineering-general"))
                .andExpect(jsonPath("$.scopes[2].accessModel").value("shared-channel-calendar"))
                .andExpect(jsonPath("$.scopes[2].capabilities[0]").value("read"));
    }

    @Test
    void calendarFacadeRejectsPrivatePersonalCalendarScopesBeforeAdapterAccess() throws Exception {
        String privateEvent = """
                {
                  "title": "Private sync",
                  "startsAt": "2026-04-26T10:00:00+02:00",
                  "endsAt": "2026-04-26T11:00:00+02:00",
                  "timezone": "Europe/Berlin",
                  "scope": {
                    "type": "private",
                    "label": "Personal calendar"
                  }
                }
                """;

        mockMvc.perform(post("/api/calendar/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(privateEvent))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.details.module").value("calendar"))
                .andExpect(jsonPath("$.details.operation").value("create-event"))
                .andExpect(jsonPath("$.details.fields['scope.type']")
                        .value("scope type must be workspace, team, or channel"));
    }

    @Test
    void calendarCreateIsDeniedByCapabilityPolicyBeforeProviderAccess() throws Exception {
        String event = """
                {
                  "title": "Planning",
                  "startsAt": "2026-04-26T10:00:00+02:00",
                  "endsAt": "2026-04-26T11:00:00+02:00",
                  "timezone": "Europe/Berlin"
                }
                """;

        mockMvc.perform(post("/api/calendar/events")
                        .with(memberJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(event))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.module").value("calendar"))
                .andExpect(jsonPath("$.details.operation").value("create-event"))
                .andExpect(jsonPath("$.details.requiredCapability").value("calendar.manage_events"))
                .andExpect(jsonPath("$.details.policyState").value("policy_blocked"));
    }

    @Test
    void calendarFacadeFailsClosedWhenContextAuthorizationDeniesAccess() throws Exception {
        when(contextAuthorizationPort.check(any()))
                .thenReturn(ContextAuthorizationDecision.deny("no matching context membership"));

        mockMvc.perform(get("/api/calendar/events")
                        .with(workspaceJwt()))
                .andExpect(status().isForbidden())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("calendar-forbidden"))
                .andExpect(jsonPath("$.details.module").value("calendar"))
                .andExpect(jsonPath("$.details.operation").value("list-events"))
                .andExpect(jsonPath("$.details.contextId").value("workspace-default"))
                .andExpect(jsonPath("$.details.permission").value("view"));
    }

    @Test
    void calendarReadFacadeExposesStableUnavailableErrorUntilNextcloudAdapterExists() throws Exception {
        mockMvc.perform(get("/api/calendar/events/event-id")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("calendar-adapter-not-configured"))
                .andExpect(jsonPath("$.details.module").value("calendar"))
                .andExpect(jsonPath("$.details.operation").value("read-event"));
    }

    @Test
    void memberCalendarApiDoesNotExposeClientSetupOrReadinessDiagnostics() throws Exception {
        mockMvc.perform(get("/api/calendar/client-setup")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/calendar/client-setup/credentials")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/calendar/client-setup/apple.mobileconfig")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void facadeRequestsUseStableValidationEnvelope() throws Exception {
        String invalidEvent = """
                {
                  "title": "",
                  "startsAt": "2026-04-26T10:00:00+02:00",
                  "endsAt": "2026-04-26T09:00:00+02:00",
                  "timezone": "Europe/Berlin"
                }
                """;

        mockMvc.perform(post("/api/calendar/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEvent))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.details.fields.title").exists())
                .andExpect(jsonPath("$.details.fields.timeRangeValid").exists());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
                .claim("groups", java.util.List.of("weave-calendar-editors")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
                        .claim("groups", java.util.List.of()))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
