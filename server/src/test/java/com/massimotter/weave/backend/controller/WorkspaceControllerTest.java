package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.service.OrganizationManifestService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceHomeService;
import com.massimotter.weave.backend.service.WorkspaceHomeRecentActivityService;
import com.massimotter.weave.backend.service.WorkspaceReleaseReadinessService;
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = WorkspaceController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        WorkspaceCapabilityService.class,
        OrganizationManifestService.class,
        WorkspaceReleaseReadinessService.class,
        WorkspaceHomeService.class,
        WorkspaceHomeRecentActivityService.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
        ContextAuthorizationProperties.class,
        WorkspaceCapabilityProperties.class,
        OAuth2ResourceServerProperties.class
})
@org.springframework.test.context.TestPropertySource(properties = {
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

    @Autowired
    private OAuth2ResourceServerProperties resourceServerProperties;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private AuditEventPublisher auditEventPublisher;

    @MockitoBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @Test
    void returnsOrganizationManifestForMemberClientWithoutAdminConsoleLeakage() throws Exception {
        // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
        // SUPPORT_SAFE_CAPABILITY_STATES_CONTRACT
        mockMvc.perform(get("/api/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("calendar-editor@example.invalid")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("weave_tenant_id", "weave-dogfood")
                                .claim("weave_organization_name", "Weave Dogfood")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                                .claim("groups", List.of("/members")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").value("org-manifest-v1"))
                .andExpect(jsonPath("$.organizationId").value("weave-dogfood"))
                .andExpect(jsonPath("$.displayName").value("Weave Dogfood"))
                .andExpect(jsonPath("$.organizationAuthUrl").value("https://auth.weave.test/realms/weave"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.diagnosticsExposed").value(false))
                .andExpect(jsonPath("$.whitelistingOwner").value("organization-admin-console"))
                .andExpect(jsonPath("$.clientResponsibilities", hasItems(
                        "accept organization auth URL, invite link, or deep link",
                        "complete OIDC Authorization Code with PKCE through the organization authority",
                        "consume effective organization manifest and capability states",
                        "render only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later member states")))
                .andExpect(jsonPath("$.adminConsoleResponsibilities", hasItems(
                        "create and bootstrap organizations",
                        "manage Keycloak identity, upstream federation, and selectable category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes")))
                .andExpect(jsonPath("$.memberCapabilityStates['platform-identity']").value("available"))
                .andExpect(jsonPath("$.memberCapabilityStates['chat-channels']").value("available"))
                .andExpect(jsonPath("$.memberCapabilityStates['calendar-events']").value("degraded"))
                .andExpect(jsonPath("$.memberCapabilityStates['files-docs']").value("available"))
                .andExpect(jsonPath("$.memberCapabilityStates['boards-tasks']").value("disabled_by_policy"))
                .andExpect(jsonPath("$.memberCapabilityStates.meetings").value("not_configured"))
                .andExpect(jsonPath("$.memberCapabilityStates['forms-contacts']").value("coming_later"))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.productApiBasePath").value("/api/files"))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.openApiTag").value("Files"))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.supportSafe").value(true))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'standard-protocol')].name")
                        .value(hasItem("Weave WebDAV projection")))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'standard-protocol')].setupPath")
                        .value(hasItem("/dav/files")))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'standard-protocol')].readiness")
                        .value(hasItem("data_plane_read_write_available")))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'mcp')]").isEmpty())
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'native-os')].setupPath")
                        .value(hasItem("/api/files/native-provider-setup")))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.credentialLifecycle.status")
                        .value("revocable_device_grants_available"))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.credentialLifecycle.lifecyclePaths", hasItems(
                        "/api/files/client-setup/credentials",
                        "/api/files/native-provider-setup")))
                .andExpect(jsonPath("$.clientAccessDiscovery.files.credentialLifecycle.secretMaterialReturned").value(false))
                .andExpect(jsonPath("$.clientAccessDiscovery.calendar.surfaces[?(@.kind == 'standard-protocol')].name")
                        .value(hasItem("Weave CalDAV/iCalendar projection")))
                .andExpect(jsonPath("$.clientAccessDiscovery.calendar.surfaces[?(@.kind == 'standard-protocol')].setupPath")
                        .value(hasItem("/caldav")))
                .andExpect(jsonPath("$.clientAccessDiscovery.calendar.surfaces[?(@.kind == 'native-os')].setupPath")
                        .value(hasItem("/api/calendar/native-sync-setup")))
                .andExpect(jsonPath("$.clientAccessDiscovery.calendar.credentialLifecycle.lifecyclePaths", hasItems(
                        "/api/calendar/client-setup/credentials",
                        "/api/calendar/client-setup/apple.mobileconfig")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.openApiTag").value("Chat domain"))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'openapi')].name")
                        .value(hasItem("Weave Chat control and context API")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'openapi')].readiness")
                        .value(hasItem("control_plane_available")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'standard-protocol')].name")
                        .value(hasItem("Weave Matrix Client-Server projection")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'standard-protocol')].setupPath")
                        .value(hasItem("/_matrix/client")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'standard-protocol')].readiness")
                        .value(hasItem("encrypted_data_plane_available")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.credentialLifecycle.status")
                        .value("session_bound_no_raw_matrix_credentials"))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].productApiBasePath")
                        .value("/_matrix/client"))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].surfaces[?(@.kind == 'standard-protocol')].name")
                        .value(hasItem("MatrixRTC Profile 0 signaling")))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].surfaces[?(@.kind == 'standard-protocol')].readiness")
                        .value(hasItems("experimental_guarded", "rtc_authorizer_required")))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].credentialLifecycle.status")
                        .value("matrix_native_oauth_distinct_from_sfu_tokens"))
                .andExpect(jsonPath("$.capabilities.calendar.grantedCapabilities", hasItems("calendar.read")))
                .andExpect(jsonPath("$.capabilities.agentRuntimeControl.policyState").value("disabled"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("matrix.weave.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("files.weave.test"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("nextcloud"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("provider.example"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("token="))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("secretref://"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("providerDiagnostics"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("Authorization: Bearer"))));
    }

    @Test
    void rejectsOrganizationManifestWhenAuthUrlWouldExposeUserInfo() throws Exception {
        String originalIssuerUri = resourceServerProperties.getJwt().getIssuerUri();
        resourceServerProperties.getJwt().setIssuerUri("https://user:pass@auth.weave.test/realms/weave");
        try {
            mockMvc.perform(get("/api/organization/manifest").with(jwt()
                            .jwt(jwt -> jwt
                                    .subject("calendar-editor@example.invalid")
                                    .claim("iss", "https://auth.example.invalid/realms/acme")
                                    .claim("weave_tenant_id", "weave-dogfood")
                                    .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                                    .claim("groups", List.of("/members")))
                            .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("organization-manifest-invalid-auth-url"));
        } finally {
            resourceServerProperties.getJwt().setIssuerUri(originalIssuerUri);
        }
    }

    @Test
    void rejectsOrganizationManifestWhenAuthUrlIsMissing() throws Exception {
        String originalIssuerUri = resourceServerProperties.getJwt().getIssuerUri();
        resourceServerProperties.getJwt().setIssuerUri(" ");
        try {
            mockMvc.perform(get("/api/organization/manifest").with(jwt()
                            .jwt(jwt -> jwt
                                    .subject("calendar-editor@example.invalid")
                                    .claim("iss", "https://auth.example.invalid/realms/acme")
                                    .claim("weave_tenant_id", "weave-dogfood")
                                    .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                                    .claim("groups", List.of("/members")))
                            .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("organization-manifest-invalid-auth-url"));
        } finally {
            resourceServerProperties.getJwt().setIssuerUri(originalIssuerUri);
        }
    }

    @Test
    void rejectsOrganizationManifestWhenTenantClaimIsMissing() throws Exception {
        mockMvc.perform(get("/api/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("calendar-editor@example.invalid")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                                .claim("groups", List.of("/members")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("organization-manifest-unauthorized"));
    }

    @Test
    void returnsConfiguredWorkspaceCapabilities() throws Exception {
        assertConfiguredWorkspaceCapabilities("/api/workspace/capabilities");
    }

    @Test
    void operatorCanReadReleaseReadinessSnapshot() throws Exception {
        assertReleaseReadinessSnapshot("/api/workspace/release-readiness");
    }

    @Test
    void releaseReadinessRejectsMembersWithoutAdminReadinessCapability() throws Exception {
        mockMvc.perform(get("/api/workspace/release-readiness").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
    }

    @Test
    void returnsWeaveHomeDailyWorkSnapshot() throws Exception {
        assertWeaveHomeSnapshot("/api/workspace/home");
    }

    @Test
    void homeProjectsOnlyContextAuthorizedSupportSafeActivityFromTheJwtCaller() throws Exception {
        when(auditEventPublisher.events()).thenReturn(List.of(
                new AuditEvent(
                        "tenant-a",
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
                        "tenant-a",
                        "workspace-private",
                        "user:private-author",
                        "files:webdav",
                        AuditAction.FILES_WEBDAV_WRITE_COMPLETED,
                        Instant.parse("2026-07-12T10:02:00Z"),
                        "home-controller-private-write",
                        AuditRedactionLevel.SUPPORT_SAFE,
                        Map.of("productPath", "/private/secret.pdf"))));
        when(contextAuthorizationPort.check(any())).thenAnswer(invocation -> {
            var request = (com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest) invocation.getArgument(0);
            return "workspace-shared".equals(request.contextId())
                    ? ContextAuthorizationDecision.allow("shared workspace")
                    : ContextAuthorizationDecision.deny("not a member");
        });

        mockMvc.perform(get("/api/workspace/home").with(jwt()
                        .jwt(token -> token
                                .subject("author-sub")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("weave_tenant_id", "tenant-a")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
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
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
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
        mockMvc.perform(get("/api/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin"))))
                                .claim("groups", List.of("/admins")))
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
        mockMvc.perform(get("/api/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
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

        mockMvc.perform(get("/api/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/home"))
                .andExpect(status().isUnauthorized());
    }

    private void assertConfiguredWorkspaceCapabilities(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
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
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("admin")))))
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
                                .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member")))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.supportSafe").value(true))
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
