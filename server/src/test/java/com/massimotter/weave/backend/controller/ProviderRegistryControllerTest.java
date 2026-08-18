package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.support.HumanJwtTestSupport;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.ProviderCoreConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import java.time.Instant;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
        InMemoryProviderSelectionRepository.class,
        ProviderCoreConfiguration.class,
        DevopsProviderConfiguration.class,
        DisabledOfficeProvider.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.calls.sfu.livekit.enabled=true",
        "weave.calls.sfu.livekit.url=",
        "weave.calls.sfu.livekit.api-key=",
        "weave.calls.sfu.livekit.api-secret=",
        "weave.calls.sfu.livekit.token-endpoint="
})
class ProviderRegistryControllerTest {

    // V01_ADMIN_HEALTH_POLICY_ENFORCEMENT

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ProviderSelectionRepository providerSelectionRepository;

    @BeforeEach
    void setUpWorkspaceCapabilitySnapshot() {
        selectDefaultProviders();
        enforceEffectiveCapabilityPolicy();
        when(workspaceCapabilityService.snapshot()).thenReturn(new WorkspaceCapabilitiesResponse(
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Weave SSO shell access is available."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Chat is available through Weave."),
                capability(WorkspaceCapabilityReadiness.READY, WorkspaceCapabilityPolicyState.ALLOWED, "Files are available through Weave."),
                capability(WorkspaceCapabilityReadiness.DEGRADED, WorkspaceCapabilityPolicyState.ALLOWED, "Calendar is degraded. Ask an admin to inspect Workspace Health."),
                capability(WorkspaceCapabilityReadiness.BLOCKED, WorkspaceCapabilityPolicyState.POLICY_BLOCKED, "Boards/tasks are blocked by your role or group policy."),
                capability(WorkspaceCapabilityReadiness.UNAVAILABLE, WorkspaceCapabilityPolicyState.DISABLED, "Weaver is disabled by workspace policy.")));
    }

    private void enforceEffectiveCapabilityPolicy() {
        doAnswer(invocation -> {
            org.springframework.security.oauth2.jwt.Jwt jwt = invocation.getArgument(0);
            List<String> roles = jwt == null
                    ? List.of()
                    : com.massimotter.weave.backend.security.NativeOrganizationClaims
                            .clientRoles(jwt, "weave-app");
            boolean allowed = roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin") || role.equals("operator"));
            if (!allowed) {
                throw new ApiErrorException(
                        HttpStatus.FORBIDDEN,
                        "capability-policy-blocked",
                        "This action is blocked by workspace role or group policy.",
                        Map.of("requiredCapability", "admin_control_plane.readiness_read", "policyState", "policy_blocked", "diagnosticsRedacted", true));
            }
            return null;
        }).when(workspaceCapabilityService).requireCapability(any(), anyString(), anyString(), anyString());
    }

    private void selectDefaultProviders() {
        providerSelectionRepository.save(selection("chat", "synapse-homeserver", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("files", "nextcloud-files", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("calendar", "nextcloud-caldav", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("boards-tasks", "openproject-primary", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("meetings-calls", "livekit", "recommended_self_hosted_default"));
        providerSelectionRepository.save(selection("documents-collaboration", "onlyoffice", "recommended_self_hosted_default"));
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
    void providerStatusRequiresWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/providers/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void providerStatusRejectsMembers() throws Exception {
        mockMvc.perform(get("/api/providers/status").with(memberJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
    }

    @Test
    void providerStatusReportsAllFacadeSeamsWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/providers/status").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseStatus").value("provider-stack-contract-v1"))
                .andExpect(jsonPath("$.providerConfigSource").value("admin-control-plane-selected-provider-mappings"))
                .andExpect(jsonPath("$.bootstrapDefaultsAreSuggestionsOnly").value(true))
                .andExpect(jsonPath("$.adminSelectedMappingsRequired").value(true))
                .andExpect(jsonPath("$.backendOwnedFacades").value(true))
                .andExpect(jsonPath("$.flutterDirectProviderCallsAllowed").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.canonicalDomainRegistry.registryVersion").value("canonical-domain-registry-v1"))
                .andExpect(jsonPath("$.canonicalDomainRegistry.providerNamesInMemberContractsAllowed").value(false))
                .andExpect(jsonPath("$.canonicalDomainRegistry.domains[*].key", hasItems(
                        "people", "spaces", "chat", "files", "documents", "calendar", "boards", "calls", "decisions", "notifications", "health", "agent-runtime-control")))
                .andExpect(jsonPath("$.canonicalDomainRegistry.domains[?(@.key == 'identity')]").isEmpty())
                .andExpect(jsonPath("$.canonicalDomainRegistry.memberStates[*]", hasItems("available", "disabled_by_policy", "not_configured", "degraded", "unavailable", "coming_later")))
                .andExpect(jsonPath("$.canonicalDomainRegistry.adminStates[*]", hasItems("provider_not_configured", "dry_run_required", "lossy_mapping_pending", "apply_blocked", "migration_ready")))
                .andExpect(jsonPath("$.canonicalDomainRegistry.lossClasses[*]", hasItems("lossless_canonical", "lossy_with_report", "blocked_nonportable", "provider_unexportable")))
                .andExpect(jsonPath("$.canonicalDomainRegistry.compatibilityAliases['boards-tasks']").value("boards"))
                .andExpect(jsonPath("$.canonicalDomainRegistry.compatibilityAliases['meetings-calls']").value("calls"))
                .andExpect(jsonPath("$.canonicalDomainRegistry.domains[?(@.key == 'boards')].portabilityRequirements[*]", hasItems("dry_run_required_before_apply", "support_safe_migration_evidence_required")))
                .andExpect(jsonPath("$.canonicalDomainRegistry.domains[?(@.key == 'boards')].adapterManifestRequirements[*]", hasItems("secret_ref_only", "support_safe_diagnostics")))
                .andExpect(jsonPath("$.domainAdapterRegistry.singleActiveAdapterEnforced").value(true))
                .andExpect(jsonPath("$.domainAdapterRegistry.memberProviderConfigurationAllowed").value(false))
                .andExpect(jsonPath("$.domainAdapterRegistry.domains[?(@.domain == 'chat')].activeAdapter", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.domainAdapterRegistry.domains[?(@.domain == 'chat')].candidates[*].diagnostics.secretsReturned", hasItems(false)))
                .andExpect(jsonPath("$.categories[*].category", hasItems(
                        "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "decisions-evidence", "manuals-help", "release-evidence", "admin-control-plane", "agent-runtime-control")))
                .andExpect(jsonPath("$.categories[?(@.category == 'identity-idm')]").isEmpty())
                .andExpect(jsonPath("$.selectedProviderMappings[*].providerKey", hasItems("synapse-homeserver", "openproject-primary")))
                .andExpect(jsonPath("$.categories[?(@.category == 'calendar')].readiness", hasItems("degraded")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].readiness", hasItems("policy_blocked")))
                .andExpect(jsonPath("$.categories[?(@.category == 'meetings-calls')].readiness", hasItems("misconfigured")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].readiness", hasItems("misconfigured")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].contract.featureCapabilities[*]", hasItems("documents.collaborate")))
                .andExpect(jsonPath("$.categories[?(@.category == 'documents-collaboration')].contract.externalAdapters[*]", hasItems("microsoft-365-office", "collabora")))
                .andExpect(jsonPath("$.categories[?(@.category == 'decisions-evidence')].contract.defaultAdapters[*]", hasItems("weave-decision-ledger")))
                .andExpect(jsonPath("$.categories[?(@.category == 'manuals-help')].contract.defaultAdapters[*]", hasItems("mkdocs-material-embedded")))
                .andExpect(jsonPath("$.categories[?(@.category == 'release-evidence')].contract.defaultAdapters[*]", hasItems("release-evidence")))
                .andExpect(jsonPath("$.categories[?(@.category == 'admin-control-plane')].contract.defaultAdapters[*]", hasItems("weave-health-facade")))
                .andExpect(jsonPath("$.categories[?(@.category == 'agent-runtime-control')].readiness", hasItems("disabled")))
                .andExpect(jsonPath("$.categories[*].contract.stableMemberImpactStates[*]", hasItems(
                        "available", "disabled_by_policy", "not_configured", "degraded", "unavailable", "coming_later")))
                .andExpect(jsonPath("$.categories[*].contract.normalMembersConfigureProviders", hasItems(false)))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].contract.defaultAdapters[*]", hasItems("weave-native")))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].contract.externalAdapters[*]", hasItems("microsoft-teams", "slack")))
                .andExpect(jsonPath("$.categories[?(@.category == 'files')].contract.externalAdapters[*]", hasItems("sharepoint", "onedrive", "smb")))
                .andExpect(jsonPath("$.categories[?(@.category == 'files')].contract.defaultAdapters[*]", hasItems("weave-native")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].contract.defaultAdapters[*]", hasItems("openproject-primary")))
                .andExpect(jsonPath("$.categories[?(@.category == 'boards-tasks')].contract.externalAdapters[*]", hasItems("microsoft-planner", "jira")))
                .andExpect(jsonPath("$.categories[*].diagnostics.secretsReturned", hasItems(false)))
                .andExpect(jsonPath("$.categories[*].diagnostics.rawProviderErrorsReturned", hasItems(false)))
                .andExpect(jsonPath("$.providers[*].module", hasItems(
                        "matrix", "files", "office", "calendar", "contacts", "forms", "boards",
                        "meetings", "source-control", "ci", "issue-tracker", "release")))
                .andExpect(jsonPath("$.providers[?(@.module == 'identity-realm')]").isEmpty())
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix-auth')]").isEmpty())
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].providerKey", hasItems("weave-native")))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].failClosed", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'matrix')].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].providerKey", hasItems("onlyoffice")))
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].supportedCapabilities[0]").isEmpty())
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].diagnostics.providerRealityLevel", hasItems("contract_only")))
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].diagnostics.memberImpact", hasItems("coming_later")))
                .andExpect(jsonPath("$.providers[?(@.module == 'office')].diagnostics.missingReadinessPrerequisites[*]", hasItems(
                        "document-runtime",
                        "callback-url",
                        "jwt-or-session-secret",
                        "storage-binding",
                        "permission-model",
                        "health-check")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].providerKey", hasItems("livekit")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].configured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].failClosed", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.activeSfuAdapter", hasItems("livekit")))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.apiKeyConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.apiSecretConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'meetings')].diagnostics.tokenEndpointConfigured", hasItems(false)))
                .andExpect(jsonPath("$.providers[?(@.module == 'contacts')].providerKey", hasItems("nextcloud-carddav")))
                .andExpect(jsonPath("$.providers[?(@.module == 'source-control')].providerKey", hasItems("gitlab-ce-foss")))
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
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("admin")))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
