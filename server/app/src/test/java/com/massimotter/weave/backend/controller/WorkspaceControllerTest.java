package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.capability.api.AdminWorkspaceController;

import com.massimotter.weave.backend.home.api.WorkspaceController;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.security.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.security.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Settings;
import com.massimotter.weave.backend.spaces.authorization.ContextAuthorizationPort;
import com.massimotter.weave.backend.spaces.port.SpaceAccessPort;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.home.application.WorkspaceHomeService;
import com.massimotter.weave.backend.files.home.FilesHomeRecentActivityService;
import com.massimotter.weave.backend.capability.readiness.WorkspaceReleaseReadinessService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {WorkspaceController.class, AdminWorkspaceController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        WorkspaceController.class, AdminWorkspaceController.class,
        com.massimotter.weave.backend.files.home.FilesHomeConfiguration.class,
        com.massimotter.weave.backend.chat.home.ChatHomeConfiguration.class,
        com.massimotter.weave.backend.boards.home.BoardsHomeConfiguration.class,
        com.massimotter.weave.backend.calendar.home.CalendarHomeConfiguration.class,
        com.massimotter.weave.backend.decisions.home.DecisionsHomeConfiguration.class,
        com.massimotter.weave.backend.testing.WorkspaceCapabilityTestInstallation.Configuration.class,
        OrganizationIdentityContextResolver.class,
        SecurityConfig.class,
        com.massimotter.weave.backend.config.AdminApiSecurityConfiguration.class,
        WorkspaceCapabilityService.class,
        WorkspaceReleaseReadinessService.class,
        WorkspaceHomeService.class,
        FilesHomeRecentActivityService.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
        ContextAuthorizationProperties.class,
        Settings.class,
        OAuth2ResourceServerProperties.class
})
@org.springframework.test.context.TestPropertySource(properties = {
        "weave.context.authorization.default-tenant-id=weave-dogfood",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave",
        "weave.workspace.chat.dependency-url=https://matrix.weave.test",
        "weave.workspace.files.dependency-url=https://files.weave.test",
        "weave.workspace.calendar.enabled=true",
        "weave.workspace.calendar.readiness=degraded",
        "weave.workspace.meetings-calls.enabled=true"
})
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean(name = "jwtDecoder")
    private JwtDecoder jwtDecoder;

    @org.springframework.boot.test.context.TestConfiguration
    static class UserDecoderConfiguration {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        JwtDecoder jwtDecoder() {
            return org.mockito.Mockito.mock(JwtDecoder.class);
        }
    }

    @MockitoBean(name = "adminApiJwtDecoder")
    private JwtDecoder adminApiJwtDecoder;

    @MockitoBean
    private AuditEventPublisher auditEventPublisher;

    @MockitoBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @MockitoBean
    private SpaceAccessPort spaceAccessPort;

    @Test
    void retiredOrganizationManifestIsNotAnAuthenticatedCompatibilityRoute() throws Exception {
        mockMvc.perform(get("/api/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt.claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsConfiguredWorkspaceCapabilities() throws Exception {
        assertConfiguredWorkspaceCapabilities("/api/workspace/capabilities");
    }

    @Test
    void operatorCanReadReleaseReadinessSnapshot() throws Exception {
        assertReleaseReadinessSnapshot("/api/admin/workspace/release-readiness");
    }

    @Test
    void releaseReadinessRejectsMembersWithoutAdminReadinessCapability() throws Exception {
        mockMvc.perform(get("/api/admin/workspace/release-readiness").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void returnsWeaveHomeDailyWorkSnapshot() throws Exception {
        assertWeaveHomeSnapshot("/api/workspace/home");
    }

    @Test
    void homeProjectsOnlyContextAuthorizedSupportSafeActivityFromTheJwtCaller() throws Exception {
        when(auditEventPublisher.events(any(com.massimotter.weave.backend.audit.AuditEventQuery.class))).thenReturn(List.of(
                new AuditEvent(
                        "weave-dogfood",
                        "workspace-shared",
                        "user:author-sub",
                        "files:webdav",
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        Instant.parse("2026-07-12T10:01:00Z"),
                        "home-controller-file-write",
                        AuditRedactionLevel.SUPPORT_SAFE,
                        Map.of(
                                "productPath", "/private/quarterly-plan.pdf",
                                "providerId", "provider-resource-42")),
                new AuditEvent(
                        "weave-dogfood",
                        "workspace-private",
                        "user:private-author",
                        "files:webdav",
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        Instant.parse("2026-07-12T10:02:00Z"),
                        "home-controller-private-write",
                        AuditRedactionLevel.SUPPORT_SAFE,
                        Map.of("productPath", "/private/secret.pdf"))));
        when(spaceAccessPort.allows(any(), any(), any(), any())).thenAnswer(invocation ->
                "workspace-shared".equals(invocation.getArgument(1)));

        mockMvc.perform(get("/api/workspace/home").with(jwt()
                        .jwt(token -> token
                                .subject("author-sub")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("weave_tenant_id", "weave-dogfood")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.recentActivity.length()").value(1))
                .andExpect(jsonPath("$.recentActivity[0].activityRef").value(org.hamcrest.Matchers.matchesPattern(
                        "activity:sha256:[0-9a-f]{64}")))
                .andExpect(jsonPath("$.recentActivity[0].actorRefHash").value(org.hamcrest.Matchers.matchesPattern(
                        "sha256:[0-9a-f]{64}")))
                .andExpect(jsonPath("$.recentActivity[0].domain").value("files"))
                .andExpect(jsonPath("$.recentActivity[0].action").value("files.webdav_write.completed"))
                .andExpect(jsonPath("$.recentActivity[0].visibility").value("workspace"))
                .andExpect(jsonPath("$.recentActivity[0].actorIsCurrentUser").value(true))
                .andExpect(jsonPath("$.recentActivity[0].supportSafe").value(true))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        not(containsString("quarterly-plan.pdf"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        not(containsString("provider-resource-42"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(
                        not(containsString("private-author"))));
    }

    @Test
    void removedMemberWeaverRoutesAreDeniedWithoutCompatibilityHandler() throws Exception {
        // Removed member Weaver routes remain denied without a compatibility handler.
        var member = jwt()
                .jwt(token -> token
                        .subject("member@example.invalid")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));

        for (String path : List.of(
                "/api/workspace/weaver/runtime-profile",
                "/api/v1/workspace/weaver/runtime-profile",
                "/api/workspace/weaver/runtime-profiles/sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "/api/v1/workspace/weaver/runtime-profiles/sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                "/api/workspace/weaver/mcp/servers/weave-domain-tools/tools",
                "/api/v1/workspace/weaver/mcp/servers/weave-domain-tools/tools")) {
            mockMvc.perform(get(path).with(member)).andExpect(status().is4xxClientError());
        }
    }

    @Test
    void returnsAdminCapabilityPolicySnapshot() throws Exception {
        mockMvc.perform(get("/api/admin/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("admin")))
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformIdentityAuthority").value("Keycloak"))
                .andExpect(jsonPath("$.federationContract").value(containsString("LDAP")))
                .andExpect(jsonPath("$.denyByDefault").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.grantedCapabilities").isArray())
                .andExpect(jsonPath("$.agentRuntimeControlPosture").value(org.hamcrest.Matchers.containsString("disabled-by-default")));
    }

    @Test
    void rejectsCapabilityPolicyForMembers() throws Exception {
        mockMvc.perform(get("/api/admin/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("forbidden"));
    }

    @Test
    void delegatedMcpScopeCannotRoamOrdinaryWorkspaceApis() throws Exception {
        mockMvc.perform(get("/api/workspace/capabilities")
                        .with(jwt().jwt(token -> token
                                        .subject("member@example.invalid")
                                        .claim("iss", "https://auth.example.invalid/realms/acme")
                                        .claim("aud", List.of("weave-backend"))
                                        .claim("azp", "weave-mcp-server")
                                        .claim("scope", "weave:mcp-backend"))
                                .authorities(new SimpleGrantedAuthority("SCOPE_weave:mcp-backend"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/workspace/capabilities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/home"))
                .andExpect(status().isUnauthorized());
    }

    private void assertConfiguredWorkspaceCapabilities(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shellAccess.enabled").value(true))
                .andExpect(jsonPath("$.shellAccess.readiness").value("ready"))
                .andExpect(jsonPath("$.chat.readiness").value("ready"))
                .andExpect(jsonPath("$.files.readiness").value("ready"))
                .andExpect(jsonPath("$.calendar.enabled").value(true))
                .andExpect(jsonPath("$.calendar.readiness").value("degraded"))
                .andExpect(jsonPath("$.boards.readiness").value("unavailable"))
                .andExpect(jsonPath("$.chat.policyState").value("allowed"))
                .andExpect(jsonPath("$.agentRuntimeControl.enabled").value(false))
                .andExpect(jsonPath("$.agentRuntimeControl.policyState").value("disabled"));
    }

    private void assertReleaseReadinessSnapshot(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("admin")))
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("ready"))
                .andExpect(jsonPath("$.checks[0].key").value("auth-contract"))
                .andExpect(jsonPath("$.checks[1].key").value("chat"))
                .andExpect(jsonPath("$.checks[2].key").value("files"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    private void assertWeaveHomeSnapshot(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(3))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.sections[*].itemCount").value(org.hamcrest.Matchers.hasSize(5)))
                .andExpect(jsonPath("$.sections[*].itemCount").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$.sections[0].summary").value("Chat is available."))
                .andExpect(jsonPath("$.sections[0].key").value("recent-channels"))
                .andExpect(jsonPath("$.sections[0].productRoute").value("weave://home/channels"))
                .andExpect(jsonPath("$.sections[1].key").value("open-tasks"))
                .andExpect(jsonPath("$.sections[1].readiness").value("unavailable"))
                .andExpect(jsonPath("$.sections[2].key").value("upcoming-meetings"))
                .andExpect(jsonPath("$.sections[2].readiness").value("degraded"))
                .andExpect(jsonPath("$.sections[3].key").value("recent-decisions"))
                .andExpect(jsonPath("$.sections[4].key").value("workspace-health"))
                .andExpect(jsonPath("$.recentActivity").isEmpty())
                .andExpect(jsonPath("$.actions[0].productRoute").value("weave://home/tasks"));
    }
}
