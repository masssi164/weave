package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.ProviderCoreConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = ProviderRegistryController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        ProviderRegistry.class,
        ProviderCoreConfiguration.class,
        DevopsProviderConfiguration.class,
        DisabledOfficeProvider.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.meetings.livekit.enabled=true",
        "weave.meetings.livekit.url=",
        "weave.meetings.livekit.api-key=",
        "weave.meetings.livekit.api-secret=",
        "weave.meetings.livekit.token-endpoint="
})
class ProviderRegistryControllerTest {

    // V01_ADMIN_HEALTH_POLICY_ENFORCEMENT

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private WorkspaceCapabilityService workspaceCapabilityService;

    @BeforeEach
    void setUpWorkspaceCapabilitySnapshot() {
        when(workspaceCapabilityService.snapshot()).thenReturn(new WorkspaceCapabilitiesResponse(
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Weave SSO shell access is available."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Chat is available through Weave."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Files are available through Weave."),
                capability(WorkspaceCapabilityReadiness.DEGRADED, WorkspaceCapabilityPolicyState.ALLOWED, "Calendar is degraded. Ask an admin to inspect Workspace Health."),
                capability(WorkspaceCapabilityReadiness.BLOCKED, WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "Boards/tasks are blocked by your role or group policy."),
                capability(WorkspaceCapabilityReadiness.UNAVAILABLE, WorkspaceCapabilityPolicyState.DISABLED, "Weaver is disabled by workspace policy.")));
    }

    @Test
    void providerStatusRequiresWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/providers/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void providerStatusRejectsMembers() throws Exception {
        mockMvc.perform(get("/api/providers/status").with(memberJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void providerStatusReportsAllFacadeSeamsWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/providers/status").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendOwnedFacades").value(true))
                .andExpect(jsonPath("$.flutterDirectProviderCallsAllowed").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.domainAdapterRegistry.singleActiveAdapterEnforced").value(true))
                .andExpect(jsonPath("$.domainAdapterRegistry.memberProviderConfigurationAllowed").value(false))
                .andExpect(jsonPath("$.domainAdapterRegistry.domains[?(@.domain == 'chat')].activeAdapter", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.domainAdapterRegistry.domains[?(@.domain == 'chat')].candidates[*].diagnostics.secretsReturned", hasItems(false)))
                .andExpect(jsonPath("$.categories[*].category", hasItems(
                        "identity-idm", "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "decisions-evidence", "manuals-help", "release-evidence", "admin-control-plane", "weaver")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].readiness", hasItems("ready")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].contract.defaultAdapters[*]", hasItems("keycloak-realm")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].contract.externalAdapters[*]", hasItems("entra-id", "generic-oidc")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].contract.choiceModels[*].choiceModel", hasItems("recommended_self_hosted_default", "external_existing_provider", "managed_cloud_provider")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].contract.choiceModels[?(@.choiceModel == 'recommended_self_hosted_default')].recommended", hasItems(true)))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')].contract.choiceModels[?(@.choiceModel == 'managed_cloud_provider')].adminRiskNotes[*]", hasItems(containsString("privacy"))))
                .andExpect(jsonPath("$.categories[?(@.category == 'calendar')].readiness", hasItems("degraded")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].readiness", hasItems("policy_blocked")))
                .andExpect(jsonPath("$.categories[?(@.category == 'meetings-calls')].readiness", hasItems("misconfigured")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].readiness", hasItems("disabled")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].contract.featureCapabilities[*]", hasItems("documents.collaborate")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].contract.externalAdapters[*]", hasItems("microsoft-365-office-graph")))
                .andExpect(jsonPath("$.categories[?(@.category == 'decisions-evidence')].contract.defaultAdapters[*]", hasItems("weave-decisions-evidence")))
                .andExpect(jsonPath("$.categories[?(@.category == 'manuals-help')].contract.defaultAdapters[*]", hasItems("mkdocs-material-embedded")))
                .andExpect(jsonPath("$.categories[?(@.category == 'release-evidence')].contract.defaultAdapters[*]", hasItems("weave-release-notes")))
                .andExpect(jsonPath("$.categories[?(@.category == 'admin-control-plane')].contract.defaultAdapters[*]", hasItems("weave-admin-console")))
                .andExpect(jsonPath("$.categories[?(@.category == 'weaver')].readiness", hasItems("disabled")))
                .andExpect(jsonPath("$.categories[*].contract.stableMemberImpactStates[*]", hasItems("usable", "disabled", "degraded", "policy-blocked")))
                .andExpect(jsonPath("$.categories[*].contract.normalMembersConfigureProviders", hasItems(false)))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].contract.defaultAdapters[*]", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].contract.externalAdapters[*]", hasItems("microsoft-teams")))
                .andExpect(jsonPath("$.categories[?(@.category == 'files')].contract.externalAdapters[*]", hasItems("sharepoint", "onedrive")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].contract.defaultAdapters[*]", hasItems("openproject-primary")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].contract.externalAdapters[*]", hasItems("microsoft-planner", "jira")))
                .andExpect(jsonPath("$.categories[*].diagnostics.secretsReturned", hasItems(false)))
                .andExpect(jsonPath("$.categories[*].diagnostics.rawProviderErrorsReturned", hasItems(false)))
                .andExpect(jsonPath("$.providers[*].module", hasItems(
                        "identity-realm", "matrix", "matrix-auth", "files", "office", "calendar", "contacts", "forms", "boards",
                        "meetings", "source-control", "ci", "issue-tracker", "release")))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].providerKey", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].failClosed", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix-auth')].providerKey", hasItems("matrix-authentication-service")))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix-auth')].failClosed", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix-auth')].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].providerKey", hasItems("onlyoffice-community")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].providerKey", hasItems("livekit")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].configured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].failClosed", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.activeProvider", hasItems("livekit")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.apiKeyConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.apiSecretConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.tokenEndpointConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'contacts')].providerKey", hasItems("nextcloud-carddav")))
                .andExpect(jsonPath("$.providers[?(@.module == 'source-control')].providerKey", hasItems("gitlab-ce-foss", "forgejo")))
                .andExpect(jsonPath("$.providers[?(@.module == 'forms')].diagnostics.dependency", hasItems("weave-backend#104")))
                .andExpect(content().string(not(containsString("matrix-meetings"))))
                .andExpect(content().string(not(containsString("WEAVE_LIVEKIT_API_KEY=secret"))))
                .andExpect(content().string(not(containsString("WEAVE_LIVEKIT_API_SECRET=secret"))))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))));
    }

    private WorkspaceCapabilityStatusResponse capability(
            WorkspaceCapabilityReadiness readiness,
            WorkspaceCapabilityPolicyState policyState,
            String impact) {
        return new WorkspaceCapabilityStatusResponse(
                policyState != WorkspaceCapabilityPolicyState.DISABLED,
                readiness,
                policyState,
                "test-profile",
                impact,
                List.of("test.capability"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("admin-123")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("admin"))))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
