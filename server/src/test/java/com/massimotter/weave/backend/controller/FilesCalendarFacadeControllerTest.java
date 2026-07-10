package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarEvent;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChange;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarChangeSet;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarScope;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.CalendarWrite;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventId;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.EventVersion;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.FreeBusyWindow;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.ScopeType;
import com.massimotter.weave.backend.calendar.domain.CalendarDomain.WriteIntent;
import com.massimotter.weave.backend.calendar.port.CalendarProviderPort;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.LiveKitMeetingsProviderProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopeResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.CallsFacadeService;
import com.massimotter.weave.backend.service.FilesFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.calendar.CalendarAdapterException;
import com.massimotter.weave.backend.security.device.DeviceCredentialAuthenticationFilter;
import com.massimotter.weave.backend.security.device.DeviceCredentialRepository;
import com.massimotter.weave.backend.security.device.DeviceCredentialService;
import com.massimotter.weave.backend.security.device.InMemoryDeviceCredentialRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {
                FilesController.class,
                FilesWebDavController.class,
                CalendarController.class,
                CalDavCalendarController.class,
                CallsController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        DeviceCredentialAuthenticationFilter.class,
        FilesCalendarFacadeControllerTest.DeviceCredentialTestConfiguration.class,
        FilesFacadeService.class,
        CalendarFacadeService.class,
        CallsFacadeService.class,
        WorkspaceCapabilityService.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
        WeaverRuntimeProperties.class,
        WorkspaceCapabilityProperties.class,
        LiveKitMeetingsProviderProperties.class,
        OAuth2ResourceServerProperties.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.security.client-id=weave-app",
        "weave.security.required-audience=weave-app",
        "weave.meetings.livekit.enabled=true",
        "weave.meetings.livekit.url=https://calls.example.invalid",
        "weave.meetings.livekit.api-key=test-api-key",
        "weave.meetings.livekit.api-secret=test-api-secret"
})
class FilesCalendarFacadeControllerTest {

    @TestConfiguration
    static class DeviceCredentialTestConfiguration {
        @Bean
        DeviceCredentialRepository deviceCredentialRepository() {
            return new InMemoryDeviceCredentialRepository();
        }

        @Bean
        DeviceCredentialService deviceCredentialService(DeviceCredentialRepository repository) {
            return new DeviceCredentialService(repository);
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @MockBean
    private ContextAuthorizationProperties contextAuthorizationProperties;

    @MockBean
    private CalendarProviderPort calendarProviderPort;

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
        CalendarAdapterException notConfigured = new CalendarAdapterException(
                CalendarAdapterException.Type.NOT_CONFIGURED,
                "Calendar facade is available, but calendar storage is not configured yet.",
                Map.of("module", "calendar"));
        when(calendarProviderPort.query(
                        any(CalendarId.class),
                        any(CalendarScope.class),
                        nullable(Instant.class),
                        nullable(Instant.class)))
                .thenThrow(notConfigured);
        when(calendarProviderPort.read(any(CalendarId.class), any(CalendarScope.class), any(EventId.class)))
                .thenThrow(notConfigured);
        when(calendarProviderPort.write(any(CalendarWrite.class))).thenThrow(notConfigured);
        when(calendarProviderPort.freeBusy(
                        any(CalendarId.class),
                        any(CalendarScope.class),
                        any(Instant.class),
                        any(Instant.class)))
                .thenThrow(notConfigured);
        when(calendarProviderPort.changes(
                        any(CalendarId.class),
                        any(CalendarScope.class),
                        nullable(String.class)))
                .thenThrow(notConfigured);
        doThrow(notConfigured).when(calendarProviderPort).delete(
                any(CalendarId.class), any(CalendarScope.class), any(EventId.class), any(EventVersion.class));
    }

    @Test
    void filesFacadeRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/files/readiness"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void filesOpenApiRootNoLongerExposesMemberDataPlane() throws Exception {
        mockMvc.perform(get("/api/files")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());
    }

    @Test
    void filesReadinessExposesMemberSafeWorkspaceCapabilityState() throws Exception {
        mockMvc.perform(get("/api/files/readiness")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.readiness").value("ready"))
                .andExpect(jsonPath("$.policyState").value("allowed"))
                .andExpect(jsonPath("$.memberImpact").value("Files are available through Weave."))
                .andExpect(jsonPath("$.grantedCapabilities", org.hamcrest.Matchers.hasItems("files.read", "files.upload")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("files.weave.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))));
    }

    @Test
    void filesNativeProviderSetupExposesWeaveOwnedOsBoundariesWithoutProviderLeaks() throws Exception {
        // NATIVE_FILES_WEBDAV_CONTROL_PLANE
        mockMvc.perform(get("/api/files/native-provider-setup")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.credentialsExposed").value(false))
                .andExpect(jsonPath("$.facadeBasePath").value("/dav/files"))
                .andExpect(jsonPath("$.credentialLifecyclePath").value("/api/files/client-setup/credentials"))
                .andExpect(jsonPath("$.listPathTemplate").value("/dav/files/{path}"))
                .andExpect(jsonPath("$.downloadPathTemplate").value("/dav/files/{path}"))
                .andExpect(jsonPath("$.uploadPath").value("/dav/files/{path}"))
                .andExpect(jsonPath("$.readiness.readiness").value("ready"))
                .andExpect(jsonPath("$.options[0].platform").value("ios"))
                .andExpect(jsonPath("$.options[0].osBoundary").value("FileProviderExtension"))
                .andExpect(jsonPath("$.options[0].available").value(false))
                .andExpect(jsonPath("$.options[0].requiredContracts[0]").value("ios-file-provider-extension"))
                .andExpect(jsonPath("$.options[1].platform").value("android"))
                .andExpect(jsonPath("$.options[1].osBoundary").value("DocumentsProvider"))
                .andExpect(jsonPath("$.options[1].available").value(false))
                .andExpect(jsonPath("$.proofHooks[0]").value("OPTIONS /dav/files"))
                .andExpect(jsonPath("$.proofHooks", org.hamcrest.Matchers.hasItem("PUT /dav/files/{path}")))
                .andExpect(jsonPath("$.proofHooks", org.hamcrest.Matchers.hasItem("WebDAV writes use Weave ETag preconditions, support-safe conflict/storage errors, and mutation audit")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("http://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token="))));
    }

    @Test
    void filesCredentialLifecycleReturnsSecretOnceAuthenticatesWebdavAndRevokes() throws Exception {
        // FILES_WEBDAV_DEVICE_CREDENTIAL_CONTROL_PLANE
        String body = """
                {"label":"Mac Finder","clientType":"webdav"}
                """;
        String createdBody = mockMvc.perform(post("/api/files/client-setup/credentials")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value(org.hamcrest.Matchers.startsWith("files_device_")))
                .andExpect(jsonPath("$.state").value("active"))
                .andExpect(jsonPath("$.principalRef").value("user:user@example.com"))
                .andExpect(jsonPath("$.clientType").value("webdav"))
                .andExpect(jsonPath("$.label").value("Mac Finder"))
                .andExpect(jsonPath("$.secretMaterialReturned").value(true))
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.startsWith("files_device_")))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andExpect(jsonPath("$.webDavBasePath").value("/dav/files"))
                .andExpect(jsonPath("$.revocationActions[0]").value(org.hamcrest.Matchers.startsWith(
                        "DELETE /api/files/client-setup/credentials/files_device_")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Bearer"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("app_password"))))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String credentialId = JsonPath.read(createdBody, "$.credentialId");
        String secret = JsonPath.read(createdBody, "$.secret");

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(httpBasic(credentialId, secret)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", org.hamcrest.Matchers.containsString("1")));

        mockMvc.perform(get("/api/files/readiness").with(httpBasic(credentialId, secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
        mockMvc.perform(get("/_matrix/client/versions").with(httpBasic(credentialId, secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));

        mockMvc.perform(get("/api/files/client-setup/credentials")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials[0].credentialId").value(credentialId))
                .andExpect(jsonPath("$.credentials[0].secretMaterialReturned").value(false))
                .andExpect(jsonPath("$.credentials[0].secret").doesNotExist());

        mockMvc.perform(request(HttpMethod.DELETE, "/api/files/client-setup/credentials/{credentialId}", credentialId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value(credentialId))
                .andExpect(jsonPath("$.state").value("revoked"))
                .andExpect(jsonPath("$.revokedAt").exists())
                .andExpect(jsonPath("$.secretMaterialReturned").value(false))
                .andExpect(jsonPath("$.revocationActions").isEmpty());

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/dav/files")
                        .with(httpBasic(credentialId, secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Weave DAV")))
                .andExpect(jsonPath("$.code").value("device-credential-invalid"));
    }

    @Test
    void calendarFacadeRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/caldav/workspace/planning.ics"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void calendarLegacyRestEventDataPlaneIsRemovedInFavorOfCaldavFacade() throws Exception {
        mockMvc.perform(get("/api/calendar/events")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/calendar/events")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/calendar/events/planning")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());

        mockMvc.perform(request(HttpMethod.PATCH, "/api/calendar/events/planning")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(request(HttpMethod.DELETE, "/api/calendar/events/planning")
                        .with(workspaceJwt()))
                .andExpect(status().isNotFound());
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
        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/caldav/private")
                        .with(workspaceJwt())
                        .header("Depth", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "caldav-scope-invalid"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))));
    }

    @Test
    void calendarFacadeFailsClosedWhenContextAuthorizationDeniesAccess() throws Exception {
        when(contextAuthorizationPort.check(any()))
                .thenReturn(ContextAuthorizationDecision.deny("no matching context membership"));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/workspace/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:calendar-query xmlns:c="urn:ietf:params:xml:ns:caldav">
                                  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
                                    <c:time-range start="20260708T000000Z" end="20260709T000000Z"/>
                                  </c:comp-filter></c:comp-filter></c:filter>
                                </c:calendar-query>
                                """))
                .andExpect(status().isForbidden())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-forbidden"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Calendar access is not allowed")));
    }

    @Test
    void calendarReadFacadeExposesStableUnavailableErrorUntilCalendarStorageExists() throws Exception {
        mockMvc.perform(get("/caldav/workspace/event-id.ics")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-adapter-not-configured"));
    }

    @Test
    void calendarClientSetupExposesSecretFreePlatformOptionsWithoutAdapterCredentials() throws Exception {
        mockMvc.perform(get("/api/calendar/client-setup")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope.type").value("workspace"))
                .andExpect(jsonPath("$.scope.label").value("Weave workspace calendar"))
                .andExpect(jsonPath("$.accessModel.type").value("workspace-team-channel-calendar"))
                .andExpect(jsonPath("$.accessModel.productScope").value("workspace-team-channel"))
                .andExpect(jsonPath("$.accessModel.privateUserCalendarsAvailable").value(false))
                .andExpect(jsonPath("$.credentialReadiness.status").value("revocable_credentials_ready"))
                .andExpect(jsonPath("$.credentialReadiness.appleProfileSigned").value(false))
                .andExpect(jsonPath("$.credentialReadiness.appleProfilePasswordIncluded").value(false))
                .andExpect(jsonPath("$.credentialReadiness.revocableCredentialsAvailable").value(true))
                .andExpect(jsonPath("$.credentialReadiness.readOnlySubscriptionTokensAvailable").value(false))
                .andExpect(jsonPath("$.credentialReadiness.backendActorCredentialsExposed").value(false))
                .andExpect(jsonPath("$.username").value("user@example.com"))
                .andExpect(jsonPath("$.endpoints.serverUrl").value("/caldav"))
                .andExpect(jsonPath("$.endpoints.caldavDiscoveryUrl")
                        .value("/caldav"))
                .andExpect(jsonPath("$.endpoints.principalUrl")
                        .value("/caldav/principals/users/user%40example.com/"))
                .andExpect(jsonPath("$.options[0].platform").value("apple"))
                .andExpect(jsonPath("$.options[0].method").value("mobileconfig"))
                .andExpect(jsonPath("$.options[0].available").value(false))
                .andExpect(jsonPath("$.options[1].platform").value("android"))
                .andExpect(jsonPath("$.options[1].method").value("sync-adapter"))
                .andExpect(jsonPath("$.options[1].available").value(false))
                .andExpect(jsonPath("$.options[1].actionUrl").doesNotExist())
                .andExpect(jsonPath("$.options[2].platform").value("desktop"))
                .andExpect(jsonPath("$.options[2].method").value("caldav-manual"))
                .andExpect(jsonPath("$.options[2].available").value(true))
                .andExpect(jsonPath("$.options[2].actionUrl").value("/api/calendar/client-setup/credentials"))
                .andExpect(jsonPath("$.options[3].platform").value("subscription"))
                .andExpect(jsonPath("$.options[3].available").value(false))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("files.weave.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("davx5://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("http://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://"))));
    }

    @Test
    void calendarCredentialLifecycleReturnsSecretOnceAuthenticatesCaldavAndRevokes() throws Exception {
        String createdBody = mockMvc.perform(post("/api/calendar/client-setup/credentials")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"Apple Calendar\",\"clientType\":\"caldav\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").value(org.hamcrest.Matchers.startsWith("calendar_device_")))
                .andExpect(jsonPath("$.state").value("active"))
                .andExpect(jsonPath("$.username").value(org.hamcrest.Matchers.startsWith("calendar_device_")))
                .andExpect(jsonPath("$.principalRef").value("user:user@example.com"))
                .andExpect(jsonPath("$.secretMaterialReturned").value(true))
                .andExpect(jsonPath("$.secret").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String credentialId = JsonPath.read(createdBody, "$.credentialId");
        String secret = JsonPath.read(createdBody, "$.secret");

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/caldav")
                        .with(httpBasic(credentialId, secret)))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", "1, calendar-access"));

        mockMvc.perform(get("/api/calendar/client-setup/credentials").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials[0].credentialId").value(credentialId))
                .andExpect(jsonPath("$.credentials[0].secretMaterialReturned").value(false))
                .andExpect(jsonPath("$.credentials[0].secret").doesNotExist());

        mockMvc.perform(request(HttpMethod.DELETE,
                        "/api/calendar/client-setup/credentials/{credentialId}", credentialId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("revoked"))
                .andExpect(jsonPath("$.secretMaterialReturned").value(false));

        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/caldav")
                        .with(httpBasic(credentialId, secret)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("device-credential-invalid"));
    }

    @Test
    void calendarNativeSyncSetupExposesWeaveOwnedOsBoundariesWithoutProviderLeaks() throws Exception {
        mockMvc.perform(get("/api/calendar/native-sync-setup")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.credentialsExposed").value(false))
                .andExpect(jsonPath("$.facadeBasePath").value("/caldav"))
                .andExpect(jsonPath("$.credentialLifecyclePath").value("/api/calendar/client-setup/credentials"))
                .andExpect(jsonPath("$.appleProfilePath").value("/api/calendar/client-setup/apple.mobileconfig"))
                .andExpect(jsonPath("$.eventSyncPathTemplate").value("/caldav/{scopePath}/{eventUid}.ics"))
                .andExpect(jsonPath("$.options[0].platform").value("ios"))
                .andExpect(jsonPath("$.options[0].osBoundary").value("CalDAVConfigurationProfile"))
                .andExpect(jsonPath("$.options[0].available").value(false))
                .andExpect(jsonPath("$.options[0].requiredContracts[0]").value("ios-signed-mobileconfig"))
                .andExpect(jsonPath("$.options[1].platform").value("android"))
                .andExpect(jsonPath("$.options[1].osBoundary").value("CalendarContractAccountSyncAdapter"))
                .andExpect(jsonPath("$.options[1].available").value(false))
                .andExpect(jsonPath("$.proofHooks[0]").value("OPTIONS /caldav"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("files.weave.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("http://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("token="))));
    }

    @Test
    void calDavOptionsAndPropfindExposeWeaveCalendarProjectionWithoutProviderLeaks() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/caldav")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("DAV", "1, calendar-access"))
                .andExpect(header().string("Allow", "OPTIONS, PROPFIND, REPORT, GET, HEAD, PUT, DELETE"));

        mockMvc.perform(request(HttpMethod.valueOf("PROPFIND"), "/caldav")
                        .header("Depth", "1")
                        .with(workspaceJwt()))
                .andExpect(status().is(207))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith("application/xml"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("<d:href>/caldav/workspace/</d:href>")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Weave workspace calendar")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Bearer"))));
    }

    @Test
    void calDavReportCalendarQueryAndFreeBusyReturnFacadeBackedCalendarData() throws Exception {
        when(calendarProviderPort.query(
                        any(CalendarId.class),
                        any(CalendarScope.class),
                        nullable(Instant.class),
                        nullable(Instant.class)))
                .thenReturn(List.of(calendarEvent("planning", "Planning", "\"etag-planning\"", CalendarScope.workspace())));
        when(calendarProviderPort.freeBusy(
                        any(CalendarId.class),
                        any(CalendarScope.class),
                        any(Instant.class),
                        any(Instant.class)))
                .thenReturn(List.of(new FreeBusyWindow(
                        Instant.parse("2026-07-08T10:00:00Z"),
                        Instant.parse("2026-07-08T11:00:00Z"))));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/workspace/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:calendar-query xmlns:c="urn:ietf:params:xml:ns:caldav">
                                  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT">
                                    <c:time-range start="20260708T000000Z" end="20260709T000000Z"/>
                                  </c:comp-filter></c:comp-filter></c:filter>
                                </c:calendar-query>
                                """))
                .andExpect(status().is(207))
                .andExpect(header().string("X-Weave-Projection", "caldav-calendar-data-plane"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("<d:href>/caldav/workspace/planning.ics</d:href>")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("SUMMARY:Planning")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/workspace/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:free-busy-query xmlns:c="urn:ietf:params:xml:ns:caldav">
                                  <c:time-range start="20260708T000000Z" end="20260709T000000Z"/>
                                </c:free-busy-query>
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Weave-Projection", "caldav-calendar-data-plane"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("BEGIN:VFREEBUSY")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("FREEBUSY:20260708T100000Z/20260708T110000Z")));
    }

    @Test
    void calDavReportMultigetAndSyncCollectionUseScopedFacadeCalendars() throws Exception {
        when(calendarProviderPort.changes(
                        any(CalendarId.class),
                        argThat(scope -> scope != null && scope.type() == ScopeType.TEAM
                                && "engineering".equals(scope.teamId())),
                        nullable(String.class)))
                .thenReturn(new CalendarChangeSet(
                        "provider-sync-2",
                        List.of(new CalendarChange(
                                "provider-sync-2",
                                new EventId("team-planning"),
                                false,
                                new EventVersion("\"etag-team\"")))));
        doReturn(calendarEvent(
                "team-planning",
                "Team planning",
                "\"etag-team\"",
                new CalendarScope(ScopeType.TEAM, "engineering", null)))
                .when(calendarProviderPort).read(
                        any(CalendarId.class),
                        argThat(scope -> scope != null && scope.type() == ScopeType.TEAM),
                        eq(new EventId("team-planning")));
        doReturn(calendarEvent(
                "channel-planning",
                "Channel planning",
                "\"etag-channel\"",
                new CalendarScope(ScopeType.CHANNEL, "engineering", "engineering-general")))
                .when(calendarProviderPort).read(
                        any(CalendarId.class),
                        argThat(scope -> scope != null && scope.type() == ScopeType.CHANNEL
                                && "engineering-general".equals(scope.channelId())),
                        eq(new EventId("channel-planning")));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/team:engineering/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <d:sync-collection xmlns:d="DAV:">
                                  <d:sync-token/>
                                  <d:sync-level>1</d:sync-level>
                                </d:sync-collection>
                                """))
                .andExpect(status().is(207))
                .andExpect(header().exists("Sync-Token"))
                .andExpect(header().string("X-Weave-Projection", "caldav-calendar-data-plane"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("/caldav/team%3Aengineering/team-planning.ics")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("SUMMARY:Team planning")));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/channel:engineering-general/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:calendar-multiget xmlns:c="urn:ietf:params:xml:ns:caldav" xmlns:d="DAV:">
                                  <d:href>/caldav/channel:engineering-general/channel-planning.ics</d:href>
                                </c:calendar-multiget>
                                """))
                .andExpect(status().is(207))
                .andExpect(header().string("X-Weave-Projection", "caldav-calendar-data-plane"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("/caldav/channel%3Aengineering-general/channel-planning.ics")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("SUMMARY:Channel planning")));

        verify(calendarProviderPort).changes(
                any(CalendarId.class),
                argThat(scope -> scope != null && scope.type() == ScopeType.TEAM
                        && "engineering".equals(scope.teamId())),
                nullable(String.class));
        verify(calendarProviderPort).read(
                any(CalendarId.class),
                argThat(scope -> scope != null && scope.type() == ScopeType.CHANNEL
                        && "engineering-general".equals(scope.channelId())),
                eq(new EventId("channel-planning")));
    }

    @Test
    void calDavReportRejectsMissingOrInvalidTimeRangeSupportSafely() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/workspace/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:calendar-query xmlns:c="urn:ietf:params:xml:ns:caldav">
                                  <c:filter><c:comp-filter name="VCALENDAR"><c:comp-filter name="VEVENT"/>
                                  </c:comp-filter></c:filter>
                                </c:calendar-query>
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "caldav-time-range-required"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))));

        mockMvc.perform(request(HttpMethod.valueOf("REPORT"), "/caldav/workspace/")
                        .with(workspaceJwt())
                        .contentType("application/xml")
                        .content("""
                                <c:free-busy-query xmlns:c="urn:ietf:params:xml:ns:caldav">
                                  <c:time-range start="2026-07-08T00:00:00Z" end="20260709T000000Z"/>
                                </c:free-busy-query>
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "caldav-time-range-invalid"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))));
    }

    @Test
    void calDavEventReadPutAndDeleteUseCalendarFacadeBoundaryAndStableErrors() throws Exception {
        mockMvc.perform(get("/caldav/workspace/planning.ics")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-adapter-not-configured"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/caldav/workspace/planning.ics")
                        .with(workspaceJwt())
                        .header("If-Match", "\"old\"")
                        .contentType("text/calendar")
                        .content("""
                                BEGIN:VCALENDAR
                                VERSION:2.0
                                BEGIN:VEVENT
                                UID:planning
                                DTSTART:20260708T100000Z
                                DTEND:20260708T110000Z
                                SUMMARY:Planning
                                END:VEVENT
                                END:VCALENDAR
                                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-adapter-not-configured"));

        mockMvc.perform(request(HttpMethod.valueOf("DELETE"), "/caldav/workspace/planning.ics")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-adapter-not-configured"));
    }

    @Test
    void calDavEventReadPutCreateUpdateAndDeleteUseFacadeBackedIcalendar() throws Exception {
        // CALDAV_GET_PUT_DELETE_FACADE_MVP
        doReturn(calendarEvent("planning", "Planning", "\"etag-existing\"", CalendarScope.workspace()))
                .when(calendarProviderPort).read(
                        any(CalendarId.class), any(CalendarScope.class), any(EventId.class));
        doReturn(calendarEvent("planning-new", "Planning", "\"etag-created\"", CalendarScope.workspace()))
                .when(calendarProviderPort).write(argThat(write -> write.intent() == WriteIntent.CREATE));
        doReturn(calendarEvent("planning", "Updated planning", "\"etag-updated\"", CalendarScope.workspace()))
                .when(calendarProviderPort).write(argThat(write -> write.intent() == WriteIntent.UPDATE));
        doNothing().when(calendarProviderPort).delete(
                any(CalendarId.class), any(CalendarScope.class), any(EventId.class), any(EventVersion.class));

        mockMvc.perform(get("/caldav/workspace/planning.ics")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/calendar;charset=UTF-8"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"planning.ics\""))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("BEGIN:VCALENDAR")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("UID:planning")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("SUMMARY:Planning")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("remote.php"))));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/caldav/workspace/planning-new.ics")
                        .with(workspaceJwt())
                        .header("If-None-Match", "*")
                        .contentType("text/calendar")
                        .content("""
                                BEGIN:VCALENDAR
                                VERSION:2.0
                                BEGIN:VEVENT
                                UID:planning-new
                                DTSTART:20260708T100000Z
                                DTEND:20260708T110000Z
                                SUMMARY:Planning
                                END:VEVENT
                                END:VCALENDAR
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/caldav/workspace/planning-new.ics"))
                .andExpect(header().string("ETag", "\"etag-created\""));

        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/caldav/workspace/planning.ics")
                        .with(workspaceJwt())
                        .header("If-Match", "\"etag-existing\"")
                        .contentType("text/calendar")
                        .content("""
                                BEGIN:VCALENDAR
                                VERSION:2.0
                                BEGIN:VEVENT
                                UID:planning
                                DTSTART:20260708T120000Z
                                DTEND:20260708T130000Z
                                SUMMARY:Updated planning
                                END:VEVENT
                                END:VCALENDAR
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Location", "/caldav/workspace/planning.ics"))
                .andExpect(header().string("ETag", "\"etag-updated\""));

        mockMvc.perform(request(HttpMethod.valueOf("DELETE"), "/caldav/workspace/planning.ics")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent());
    }

    @Test
    void callsNativeBoundarySetupExposesProviderNeutralCallkitAndTelecomContract() throws Exception {
        mockMvc.perform(get("/api/calls/native-boundary-setup")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.credentialsExposed").value(false))
                .andExpect(jsonPath("$.facadeBasePath").value("/api/calls"))
                .andExpect(jsonPath("$.joinGrantPathTemplate").value("/api/calls/meetings/{meetingId}/join-grants"))
                .andExpect(jsonPath("$.signalingBoundary")
                        .value("Weave meeting invitations and short-lived join grants drive native incoming-call state."))
                .andExpect(jsonPath("$.options[0].platform").value("ios"))
                .andExpect(jsonPath("$.options[0].osBoundary").value("CallKitPushKit"))
                .andExpect(jsonPath("$.options[0].available").value(false))
                .andExpect(jsonPath("$.options[0].requiredContracts[0]").value("ios-callkit-reporting"))
                .andExpect(jsonPath("$.options[1].platform").value("android"))
                .andExpect(jsonPath("$.options[1].osBoundary").value("TelecomConnectionService"))
                .andExpect(jsonPath("$.options[1].requiredContracts[0]").value("android-telecom-connection-service"))
                .andExpect(jsonPath("$.proofHooks[0]").value("GET /api/calls/native-boundary-setup"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("LiveKit"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("wss://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("https://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("apiSecret"))));
    }

    @Test
    void callsControlPlaneCreatesJoinGrantsAndLeavesWithoutProviderSecrets() throws Exception {
        String callId = JsonPath.read(mockMvc.perform(post("/api/calls")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "spaceId": "workspace-default",
                                  "title": "Planning call",
                                  "linkedCalendarRefs": ["calendar:event:planning"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediaProvider").value("livekit"))
                .andExpect(jsonPath("$.joinAvailable").value(true))
                .andExpect(jsonPath("$.roomRef").exists())
                .andReturn()
                .getResponse()
                .getContentAsString(), "$.callId");

        mockMvc.perform(get("/api/calls/{id}", callId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value(callId))
                .andExpect(jsonPath("$.mediaProvider").value("livekit"));

        mockMvc.perform(post("/api/calls/{id}/join", callId)
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"role\":\"participant\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value(callId))
                .andExpect(jsonPath("$.roomRef").exists())
                .andExpect(jsonPath("$.mediaProvider").value("livekit"))
                .andExpect(jsonPath("$.joinUrl").value("https://calls.example.invalid"))
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("test-api-secret"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("apiSecret"))));

        mockMvc.perform(post("/api/calls/{id}/leave", callId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.callId").value(callId))
                .andExpect(jsonPath("$.left").value(true))
                .andExpect(jsonPath("$.auditRef").exists());
    }


    @Test
    void calendarAppleProfileRequiresAuthenticatedWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/calendar/client-setup/apple.mobileconfig"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void calendarAppleProfileDownloadStaysFailClosedUntilSigningExists() throws Exception {
        mockMvc.perform(get("/api/calendar/client-setup/apple.mobileconfig")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().exists("X-Request-Id"))
                .andExpect(jsonPath("$.code").value("calendar-apple-profile-unavailable"))
                .andExpect(jsonPath("$.details.operation").value("download-apple-mobileconfig"))
                .andExpect(jsonPath("$.details.requiresSignedProfile").value(true))
                .andExpect(jsonPath("$.details.passwordIncluded").value(false))
                .andExpect(jsonPath("$.details.backendActorCredentialsExposed").value(false));
    }

    @Test
    void facadeRequestsUseStableValidationEnvelope() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("PUT"), "/caldav/workspace/invalid.ics")
                        .with(workspaceJwt())
                        .contentType("text/calendar")
                        .content("BEGIN:VCALENDAR\nEND:VCALENDAR\n"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Weave-Error-Code", "calendar-ics-invalid"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user@example.com")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                .claim("aud", java.util.List.of("weave-app"))
                .claim("weave_tenant_id", "tenant-default")
                .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
                .claim("groups", java.util.List.of("weave-calendar-editors", "weave-meeting-hosts")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private CalendarEvent calendarEvent(
            String id,
            String title,
            String etag,
            CalendarScope scope) {
        return new CalendarEvent(
                new CalendarId("user@example.com"),
                new EventId(id),
                scope,
                title,
                "Roadmap sync",
                LocalDateTime.parse("2026-07-08T10:00:00"),
                LocalDateTime.parse("2026-07-08T11:00:00"),
                ZoneId.of("UTC"),
                false,
                "Room 1",
                List.of(),
                null,
                new EventVersion(etag),
                null);
    }
}
