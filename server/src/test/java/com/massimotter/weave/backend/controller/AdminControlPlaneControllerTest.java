package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.ProviderCoreConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import java.time.Instant;
import com.massimotter.weave.backend.service.AdminControlPlaneService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = AdminControlPlaneController.class,
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
        DisabledOfficeProvider.class,
        AdminControlPlaneService.class,
        AdminControlPlaneControllerTest.AuditTestConfig.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.meetings.livekit.enabled=true",
        "weave.meetings.livekit.api-key=server-test-key-that-must-never-appear",
        "weave.meetings.livekit.api-secret=server-test-secret-that-must-never-appear"
})
class AdminControlPlaneControllerTest {

    // V01_ORG_CONTROL_PLANE_PROVIDER_FACADE

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ProviderSelectionRepository providerSelectionRepository;

    @BeforeEach
    void setUpWorkspaceCapabilitySnapshot() {
        selectDefaultProviders();
        WorkspaceCapabilitiesResponse capabilities = new WorkspaceCapabilitiesResponse(
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "SSO ready."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Chat ready."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Files ready."),
                capability(WorkspaceCapabilityReadiness.DEGRADED, WorkspaceCapabilityPolicyState.ALLOWED, "Calendar degraded."),
                capability(WorkspaceCapabilityReadiness.BLOCKED, WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "Boards blocked."),
                capability(WorkspaceCapabilityReadiness.UNAVAILABLE, WorkspaceCapabilityPolicyState.DISABLED, "Weaver disabled."));
        when(workspaceCapabilityService.snapshot()).thenReturn(capabilities);
        when(workspaceCapabilityService.policySnapshot(org.mockito.ArgumentMatchers.any())).thenReturn(new WorkspaceCapabilityPolicyResponse(
                "identity/IDM",
                "Keycloak",
                "OIDC/SAML adapter contract; Keycloak is the self-hosted default, not a product lock-in",
                "JWT realm roles plus groups claims from the selected IDM",
                List.of("admin"),
                List.of("weave-board-editors"),
                List.of("workspace-admin", "group:weave-board-editors"),
                List.of("chat.read", "files.read", "boards.update_task", "weaver.exec_disabled"),
                true,
                true,
                "disabled-by-default; per-user Dockerized Weaver runtime may only be generated from org policy later"));
    }

    private void selectDefaultProviders() {
        providerSelectionRepository.save(selection("identity-idm", "keycloak-realm", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("chat", "synapse-homeserver", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("files", "nextcloud-files", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("calendar", "nextcloud-caldav", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("boards-tasks", "openproject-primary", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("meetings-calls", "livekit", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("documents-collaboration", "onlyoffice-community", "recommended_self_hosted_default"));
    }

    private ProviderSelection selection(String category, String providerKey, String choiceModel) {
        return new ProviderSelection(
                category,
                providerKey,
                choiceModel,
                "secretref://weave/provider/" + providerKey,
                "actor:test-admin",
                Instant.parse("2026-05-24T18:00:00Z"),
                true,
                true,
                false,
                List.of());
    }

    @Test
    void adminControlPlaneRejectsMembers() throws Exception {
        mockMvc.perform(get("/api/admin/control-plane").with(memberJwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminControlPlaneOverviewIsSupportSafeAndProviderNeutral() throws Exception {
        mockMvc.perform(get("/api/admin/control-plane").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("admin-control-plane-v1"))
                .andExpect(jsonPath("$.organizationId").value("weave-dogfood"))
                .andExpect(jsonPath("$.recommendedIdentityBroker").value("keycloak"))
                .andExpect(jsonPath("$.providerConfigSource").value("admin-control-plane-selected-provider-mappings"))
                .andExpect(jsonPath("$.bootstrapDefaultsAreSuggestionsOnly").value(true))
                .andExpect(jsonPath("$.backendOwnedFacades").value(true))
                .andExpect(jsonPath("$.denyByDefaultPolicy").value(true))
                .andExpect(jsonPath("$.memberClientMayConfigureProviders").value(false))
                .andExpect(jsonPath("$.categories[*].category", hasItems(
                        "identity-idm", "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "weaver")))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].selectedByAdmin", hasItems(true)))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].selectedProviderKey", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.selectedProviderMappings[*].category", hasItems("identity-idm", "chat", "files")))
                .andExpect(jsonPath("$.selectedProviderMappings[*].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.whitelist.denyByDefault").value(true))
                .andExpect(jsonPath("$.whitelist.normalMembersMayAuthorPolicy").value(false))
                .andExpect(jsonPath("$.whitelist.stableMemberImpactStates[*]", hasItems("ready", "disabled", "degraded", "policy-blocked")))
                .andExpect(jsonPath("$.whitelist.profileCapabilities['guest-deny-default']").isArray())
                .andExpect(jsonPath("$.secretRefs[*].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.secretRefs[*].rawSecretExposed", hasItems(false)))
                .andExpect(jsonPath("$.adminApiRoutes.policy").value("/api/admin/policies/capability-whitelist"))
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void providerSelectionRejectsRawSecretsAndUnknownChoiceModels() throws Exception {
        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"chat\",\"providerKey\":\"slack\",\"choiceModel\":\"external_existing_provider\",\"secretRef\":\"xoxb-raw-token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("provider-selection-secretref-invalid"))
                .andExpect(content().string(not(containsString("xoxb-raw-token"))));

        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"chat\",\"providerKey\":\"slack\",\"choiceModel\":\"hardcoded_default\",\"secretRef\":\"secretref://weave/provider/slack\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("provider-selection-choice-model-invalid"));
    }

    @Test
    void adminReadinessTestsAndPolicyUpdatesAreAuditedAndRedacted() throws Exception {
        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"chat\",\"providerKey\":\"slack\",\"choiceModel\":\"external_existing_provider\",\"secretRef\":\"secretref://weave/provider/slack\",\"lossyMappingNotes\":[\"Slack thread/broadcast semantics require migration dry-run.\"],\"reason\":\"compare external provider\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("chat"))
                .andExpect(jsonPath("$.providerKey").value("slack"))
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.migrationDryRunRequired").value(true));

        mockMvc.perform(post("/api/admin/providers/readiness-tests")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerKey\":\"livekit\",\"testKind\":\"readiness\",\"secretRef\":\"secretref://weave/provider/livekit\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("livekit"))
                .andExpect(jsonPath("$.auditEventPublished").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.rawSecretExposed").value(false))
                .andExpect(jsonPath("$.diagnostics.secretsReturned").value(false));

        mockMvc.perform(patch("/api/admin/policies/capability-whitelist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileKey\":\"workspace-admin\",\"capabilityKeys\":[\"chat.read\",\"calendar.manage_events\"],\"reason\":\"grant through Admin Console\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.denyByDefault").value(true));

        mockMvc.perform(get("/api/admin/audit/events").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("provider.readiness.tested", "admin.policy.updated")))
                .andExpect(jsonPath("$[*].payload.providerConfigSource", hasItems("admin-control-plane-selected-provider-mappings")))
                .andExpect(jsonPath("$[*].payload.token", hasItems("[redacted]")))
                .andExpect(jsonPath("$[*].payload.apiSecret", hasItems("[redacted]")))
                .andExpect(jsonPath("$[*].payload.rawProviderError", hasItems("[redacted:provider-error]")))
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))))
                .andExpect(content().string(not(containsString("Bearer secret"))));
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
                        .claim("weave_tenant", "weave-dogfood")
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

    @TestConfiguration
    static class AuditTestConfig {
        @Bean
        AuditEventPublisher auditEventPublisher() {
            return new InMemoryAuditEventPublisher();
        }

        @Bean
        ProviderSelectionRepository providerSelectionRepository() {
            return new InMemoryProviderSelectionRepository();
        }
    }
}
