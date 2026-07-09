package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WeaverRuntimeProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.service.OrganizationManifestService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceHomeService;
import com.massimotter.weave.backend.service.WorkspaceReleaseReadinessService;
import com.massimotter.weave.backend.service.WeaverMcpBridgeService;
import com.massimotter.weave.backend.service.WeaverRuntimeService;
import com.massimotter.weave.backend.weaver.WeaverToolRegistry;
import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        WeaverRuntimeService.class,
        WeaverToolRegistry.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
        ContextAuthorizationProperties.class,
        WeaverRuntimeProperties.class,
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

    @Autowired
    private WeaverRuntimeService weaverRuntimeService;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @MockBean
    private WeaverMcpBridgeService weaverMcpBridgeService;

    @Test
    void returnsOrganizationManifestForMemberClientWithoutAdminConsoleLeakage() throws Exception {
        // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
        // SUPPORT_SAFE_CAPABILITY_STATES_CONTRACT
        mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("calendar-editor@example.invalid")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("weave_tenant_id", "weave-dogfood")
                                .claim("weave_organization_name", "Weave Dogfood")
                                .claim("realm_access", Map.of("roles", List.of()))
                                .claim("groups", List.of("weave-calendar-editors", "weave-meeting-hosts")))
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
                        "complete SSO with the selected identity provider",
                        "consume effective organization manifest and capability states",
                        "render only available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later member states")))
                .andExpect(jsonPath("$.adminConsoleResponsibilities", hasItems(
                        "create and bootstrap organizations",
                        "select and configure identity providers and category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes")))
                .andExpect(jsonPath("$.memberCapabilityStates['idm-rbac']").value("available"))
                .andExpect(jsonPath("$.memberCapabilityStates['chat-channels']").value("disabled_by_policy"))
                .andExpect(jsonPath("$.memberCapabilityStates['calendar-events']").value("degraded"))
                .andExpect(jsonPath("$.memberCapabilityStates['files-docs']").value("disabled_by_policy"))
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
                .andExpect(jsonPath("$.clientAccessDiscovery.files.surfaces[?(@.kind == 'mcp')].readiness")
                        .value(hasItem("read_allowlist_available_write_cutover_blocked")))
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
                        .value(hasItem("Weave Chat API")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'standard-protocol')].name")
                        .value(hasItem("Matrix-compatible transport and federation projection")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.surfaces[?(@.kind == 'standard-protocol')].setupPath")
                        .value(hasItem("/_matrix/client")))
                .andExpect(jsonPath("$.clientAccessDiscovery.chat.credentialLifecycle.status")
                        .value("session_bound_no_raw_matrix_credentials"))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].surfaces[?(@.kind == 'native-os')].setupPath")
                        .value(hasItem("/api/calls/native-boundary-setup")))
                .andExpect(jsonPath("$.clientAccessDiscovery['meetings-calls'].surfaces[?(@.kind == 'standard-protocol')].readiness")
                        .value(hasItem("boundary_only")))
                .andExpect(jsonPath("$.capabilities.calendar.grantedCapabilities", hasItems("calendar.manage_events")))
                .andExpect(jsonPath("$.capabilities.weaver.policyState").value("disabled"))
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
            mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                            .jwt(jwt -> jwt
                                    .subject("calendar-editor@example.invalid")
                                    .claim("iss", "https://auth.example.invalid/realms/acme")
                                    .claim("weave_tenant_id", "weave-dogfood")
                                    .claim("realm_access", Map.of("roles", List.of()))
                                    .claim("groups", List.of("weave-calendar-editors")))
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
            mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                            .jwt(jwt -> jwt
                                    .subject("calendar-editor@example.invalid")
                                    .claim("iss", "https://auth.example.invalid/realms/acme")
                                    .claim("weave_tenant_id", "weave-dogfood")
                                    .claim("realm_access", Map.of("roles", List.of()))
                                    .claim("groups", List.of("weave-calendar-editors")))
                            .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value("organization-manifest-invalid-auth-url"));
        } finally {
            resourceServerProperties.getJwt().setIssuerUri(originalIssuerUri);
        }
    }

    @Test
    void rejectsOrganizationManifestWhenTenantClaimIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("calendar-editor@example.invalid")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of()))
                                .claim("groups", List.of("weave-calendar-editors")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("organization-manifest-unauthorized"));
    }

    @Test
    void returnsConfiguredWorkspaceCapabilities() throws Exception {
        assertConfiguredWorkspaceCapabilities("/api/workspace/capabilities");
        assertConfiguredWorkspaceCapabilities("/api/v1/workspace/capabilities");
    }

    @Test
    void operatorCanReadReleaseReadinessSnapshot() throws Exception {
        assertReleaseReadinessSnapshot("/api/workspace/release-readiness");
        assertReleaseReadinessSnapshot("/api/v1/workspace/release-readiness");
    }

    @Test
    void releaseReadinessRejectsMembersWithoutAdminReadinessCapability() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/release-readiness").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
    }

    @Test
    void returnsWeaveHomeDailyWorkSnapshot() throws Exception {
        assertWeaveHomeSnapshot("/api/workspace/home");
        assertWeaveHomeSnapshot("/api/v1/workspace/home");
    }

    @Test
    void returnsFailClosedWeaverRuntimeProfile() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/weaver/runtime-profile").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("member@example.invalid")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.runtimeKind").value("per-user-docker"))
                .andExpect(jsonPath("$.generatedFrom").value("workspace-capability-policy"))
                .andExpect(jsonPath("$.posture").value("disabled-by-default"))
                .andExpect(jsonPath("$.execEnabled").value(false))
                .andExpect(jsonPath("$.elevatedEnabled").value(false))
                .andExpect(jsonPath("$.auditRequired").value(true));
    }

    @Test
    void returnsAdminCapabilityPolicySnapshot() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("admin")))
                                .claim("groups", List.of("weave-board-editors")))
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultIdmProvider").value("OIDC/SAML selected IDM"))
                .andExpect(jsonPath("$.denyByDefault").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.grantedCapabilities").isArray())
                .andExpect(jsonPath("$.weaverRuntimePosture").value(org.hamcrest.Matchers.containsString("disabled-by-default")));
    }

    @Test
    void rejectsCapabilityPolicyForMembers() throws Exception {
        mockMvc.perform(get("/api/v1/workspace/capability-policy").with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
    }

    @Test
    void returnsContractBridgeDiscoveryEnvelope() throws Exception {
        String runtimeProfileHash = runtimeProfileHash();
        when(weaverMcpBridgeService.discoverMcpTools(any(), eq(runtimeProfileHash), eq("weave-domain-tools")))
                .thenReturn(new BridgeDiscoveryResponse(runtime(runtimeProfileHash), new WeaveMcpToolCatalog("weave-domain-tools", MemberMcpDomainDefinition.CONTRACT_VERSION, List.of())));
        mockMvc.perform(get("/api/v1/workspace/weaver/mcp/servers/weave-domain-tools/tools")
                        .param("runtimeProfileHash", runtimeProfileHash)
                        .with(jwt().jwt(jwt -> jwt
                                        .subject("member@example.invalid")
                                        .claim("iss", "https://auth.example.invalid/realms/acme")
                                        .claim("realm_access", Map.of("roles", List.of("member")))
                                        .claim("groups", List.of("weave-weaver-runtime", "weave-weaver-pilot")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.runtime.runtimeProfileHash").value(org.hamcrest.Matchers.startsWith("sha256:")))
                .andExpect(jsonPath("$.catalog.serverNamespace").value("weave-domain-tools"))
                .andExpect(jsonPath("$.catalog.contractVersion").value(MemberMcpDomainDefinition.CONTRACT_VERSION))
                .andExpect(jsonPath("$.catalog.tools").isArray());
    }

    @Test
    void returnsContractBridgeInvocationEnvelope() throws Exception {
        String runtimeProfileHash = runtimeProfileHash();
        when(weaverMcpBridgeService.invokeMcpTool(any(), eq("weave-domain-tools"), eq("files.read"), any()))
                .thenReturn(new BridgeInvocationResponse(
                        "files.read",
                        ToolInvocationStatus.DENIED,
                        "audit://weaver-tool/files.read/blocked",
                        true,
                        List.of(new WeaveMcpContentBlock("text", "blocked", null, Map.of("status", "DENIED"))),
                        Map.of("supportSafe", true)));
        mockMvc.perform(post("/api/v1/workspace/weaver/mcp/servers/weave-domain-tools/tools/files.read:invoke")
                        .with(jwt().jwt(jwt -> jwt
                                        .subject("member@example.invalid")
                                        .claim("iss", "https://auth.example.invalid/realms/acme")
                                        .claim("realm_access", Map.of("roles", List.of("member")))
                                        .claim("groups", List.of("weave-weaver-runtime", "weave-weaver-pilot")))
                                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName": "files.read",
                                  "arguments": {"spaceRef": "space:control-room"},
                                  "runtime": {
                                    "orgRef": {"value": "org:workspace"},
                                    "userRef": {"value": "user:member-example-invalid"},
                                    "runtimeProfileRef": {"value": "weave-runtime-profile://%s"},
                                    "runtimeProfileHash": "%s",
                                    "runtimeTokenRef": {"value": "credentialref://weave/runtime/short-lived/user-member-example-invalid"},
                                    "auditRef": "audit://weaver-mcp/weave-domain-tools/discover",
                                    "capabilityGrants": ["files.read", "weaver.exec_disabled"],
                                    "allowedTools": ["files.read"]
                                  }
                                }
                                """.formatted(runtimeProfileHash, runtimeProfileHash)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toolName").value("files.read"))
                .andExpect(jsonPath("$.status").value("DENIED"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.structuredContent.supportSafe").value(true));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/workspace/capabilities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/capabilities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/home"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/home"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/weaver/runtime-profile"))
                .andExpect(status().isUnauthorized());
    }

    private RuntimeInvocationContext runtime(String runtimeProfileHash) {
        return new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef("user:test"),
                new WeaveMcpRef("weave-runtime-profile://" + runtimeProfileHash),
                runtimeProfileHash,
                new WeaveMcpRef("credentialref://weave/runtime/short-lived/test"),
                "audit://weaver-mcp/weave-domain-tools/discover",
                null,
                null,
                List.of(),
                List.of());
    }

    private String runtimeProfileHash() {
        return weaverRuntimeService.profileFor(org.springframework.security.oauth2.jwt.Jwt.withTokenValue("token")
                        .header("alg", "none")
                        .claim("sub", "member@example.invalid")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("realm_access", Map.of("roles", List.of("member")))
                        .claim("groups", List.of("weave-weaver-runtime", "weave-weaver-pilot"))
                        .issuedAt(java.time.Instant.now())
                        .expiresAt(java.time.Instant.now().plusSeconds(300))
                        .build())
                .runtimeProfileHash();
    }

    private void assertConfiguredWorkspaceCapabilities(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("member"))))
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
                .andExpect(jsonPath("$.weaver.enabled").value(false))
                .andExpect(jsonPath("$.weaver.policyState").value("disabled"));
    }

    private void assertReleaseReadinessSnapshot(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("realm_access", Map.of("roles", List.of("operator"))))
                        .authorities(
                                new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                                new SimpleGrantedAuthority("ROLE_OPERATOR"))))
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
                                .claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.sections[0].key").value("recent-channels"))
                .andExpect(jsonPath("$.sections[0].productRoute").value("weave://home/channels"))
                .andExpect(jsonPath("$.sections[1].key").value("open-tasks"))
                .andExpect(jsonPath("$.sections[1].readiness").value("unavailable"))
                .andExpect(jsonPath("$.sections[2].key").value("upcoming-meetings"))
                .andExpect(jsonPath("$.sections[2].readiness").value("degraded"))
                .andExpect(jsonPath("$.sections[3].key").value("recent-decisions"))
                .andExpect(jsonPath("$.sections[4].key").value("workspace-health"))
                .andExpect(jsonPath("$.actions[0].productRoute").value("weave://home/tasks"));
    }
}
