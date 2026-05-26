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
import com.massimotter.weave.backend.service.WeaverRuntimeService;
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
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
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
        WeaverRuntimeService.class,
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
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.local/realms/weave",
        "weave.workspace.chat.dependency-url=https://matrix.weave.local",
        "weave.workspace.files.dependency-url=https://files.weave.local",
        "weave.workspace.calendar.enabled=true",
        "weave.workspace.calendar.readiness=degraded"
})
class WorkspaceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2ResourceServerProperties resourceServerProperties;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private AuditEventPublisher auditEventPublisher;

    @Test
    void returnsOrganizationManifestForMemberClientWithoutAdminConsoleLeakage() throws Exception {
        // V01_ORG_MANIFEST_CLIENT_ADMIN_SPLIT
        mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                        .jwt(jwt -> jwt
                                .subject("calendar-editor@example.invalid")
                                .claim("weave_tenant_id", "weave-dogfood")
                                .claim("weave_organization_name", "Weave Dogfood")
                                .claim("realm_access", Map.of("roles", List.of()))
                                .claim("groups", List.of("weave-calendar-editors")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manifestVersion").value("org-manifest-v1"))
                .andExpect(jsonPath("$.organizationId").value("weave-dogfood"))
                .andExpect(jsonPath("$.displayName").value("Weave Dogfood"))
                .andExpect(jsonPath("$.organizationAuthUrl").value("https://auth.weave.local/realms/weave"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerConfigurationExposed").value(false))
                .andExpect(jsonPath("$.diagnosticsExposed").value(false))
                .andExpect(jsonPath("$.whitelistingOwner").value("organization-admin-console"))
                .andExpect(jsonPath("$.clientResponsibilities", hasItems(
                        "accept organization auth URL, invite link, or deep link",
                        "complete SSO with the selected identity provider",
                        "consume effective organization manifest and capability states",
                        "render only ready, disabled, degraded, or policy-blocked member states")))
                .andExpect(jsonPath("$.adminConsoleResponsibilities", hasItems(
                        "create and bootstrap organizations",
                        "select and configure identity providers and category providers",
                        "manage provider endpoint URLs, rotation, readiness, and support-safe diagnostics",
                        "manage users, groups, roles, capability profiles, and deny-by-default policy",
                        "own provider, tool, and agent whitelisting plus privacy/compliance risk notes",
                        "audit organization-wide defaults and administrative changes")))
                .andExpect(jsonPath("$.memberCapabilityStates['identity-idm']").value("ready"))
                .andExpect(jsonPath("$.memberCapabilityStates.chat").value("policy-blocked"))
                .andExpect(jsonPath("$.memberCapabilityStates.calendar").value("degraded"))
                .andExpect(jsonPath("$.memberCapabilityStates.files").value("policy-blocked"))
                .andExpect(jsonPath("$.memberCapabilityStates['boards-tasks']").value("disabled"))
                .andExpect(jsonPath("$.memberCapabilityStates.weaver").value("disabled"))
                .andExpect(jsonPath("$.capabilities.calendar.grantedCapabilities", hasItems("calendar.manage_events")))
                .andExpect(jsonPath("$.capabilities.weaver.policyState").value("disabled"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("matrix.weave.local"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("files.weave.local"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("providerDiagnostics"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content().string(not(containsString("Authorization: Bearer"))));
    }

    @Test
    void rejectsOrganizationManifestWhenAuthUrlWouldExposeUserInfo() throws Exception {
        String originalIssuerUri = resourceServerProperties.getJwt().getIssuerUri();
        resourceServerProperties.getJwt().setIssuerUri("https://user:pass@auth.weave.local/realms/weave");
        try {
            mockMvc.perform(get("/api/v1/organization/manifest").with(jwt()
                            .jwt(jwt -> jwt
                                    .subject("calendar-editor@example.invalid")
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
    void returnsReleaseReadinessSnapshot() throws Exception {
        assertReleaseReadinessSnapshot("/api/workspace/release-readiness");
        assertReleaseReadinessSnapshot("/api/v1/workspace/release-readiness");
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
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden());
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

    private void assertConfiguredWorkspaceCapabilities(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("member"))))
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
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("ready"))
                .andExpect(jsonPath("$.checks[0].key").value("auth-contract"))
                .andExpect(jsonPath("$.checks[1].key").value("chat"))
                .andExpect(jsonPath("$.checks[2].key").value("files"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    private void assertWeaveHomeSnapshot(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .jwt(jwt -> jwt.claim("realm_access", Map.of("roles", List.of("member"))))
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
