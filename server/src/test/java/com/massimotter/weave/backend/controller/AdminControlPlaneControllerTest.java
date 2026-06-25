package com.massimotter.weave.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.ProviderCoreConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import com.massimotter.weave.backend.model.WorkspaceCapabilityReadiness;
import com.massimotter.weave.backend.model.WorkspaceCapabilityStatusResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyDenyResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.provider.InMemoryProviderSelectionRepository;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderSelection;
import com.massimotter.weave.backend.provider.ProviderSelectionRepository;
import java.time.Instant;
import com.massimotter.weave.backend.service.AdminControlPlaneService;
import com.massimotter.weave.backend.service.InMemoryOrganizationBootstrapRepository;
import com.massimotter.weave.backend.service.OrganizationBootstrapRepository;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private WorkspaceCapabilityService workspaceCapabilityService;

    @Autowired
    private ProviderSelectionRepository providerSelectionRepository;

    @BeforeEach
    void setUpWorkspaceCapabilitySnapshot() {
        selectDefaultProviders();
        enforceEffectiveCapabilityPolicy();
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
                "OIDC/SAML selected IDM",
                "OIDC/SAML adapter contract; Keycloak is only the dogfood default, not product truth",
                "OIDC role claims plus group claims from the selected IDM",
                List.of("admin"),
                List.of("weave-board-editors"),
                List.of("workspace-admin", "group:weave-board-editors"),
                List.of("chat.read", "files.read", "boards.update_task", "admin.policy.edit", "admin.provider.configure", "weaver.exec_disabled"),
                true,
                true,
                "disabled-by-default; per-user Dockerized Weaver runtime may only be generated from org policy later"));
        when(workspaceCapabilityService.effectivePolicySnapshot(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(new EffectivePolicyResponse(
                "admin-123",
                "weave-dogfood",
                "organization",
                List.of("https://auth.example.invalid/realms/weave"),
                List.of("weave-board-editors"),
                List.of("admin"),
                List.of("context_admin"),
                List.of("role_claim:admin", "group_claim:weave-board-editors"),
                List.of("chat.read", "files.read", "boards.update_task", "admin.policy.edit", "admin.provider.configure", "weaver.exec_disabled"),
                List.of(new EffectivePolicyDenyResponse("weaver.enabled", "weaver runtime is disabled unless an organization policy group explicitly grants it", "deny-by-default-capability-policy")),
                List.of("member-visible states remain available, disabled_by_policy, not_configured, degraded, unavailable, or coming_later"),
                List.of("effective-policy-preview:admin-123"),
                true,
                true,
                "issuer+subject:https://auth.example.invalid/realms/weave#admin-123",
                false));
    }

    private void enforceEffectiveCapabilityPolicy() {
        doAnswer(invocation -> {
            org.springframework.security.oauth2.jwt.Jwt jwt = invocation.getArgument(0);
            String capability = invocation.getArgument(1);
            List<String> roles = jwt == null
                    ? List.of()
                    : ((Map<String, Object>) jwt.getClaimAsMap("realm_access"))
                            .getOrDefault("roles", List.of()) instanceof List<?> roleValues
                                    ? roleValues.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                                    : List.of();
            boolean allowed = switch (capability) {
                case "admin_control_plane.readiness_read" -> roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin") || role.equals("operator"));
                case "admin.policy.edit", "admin.provider.configure" -> roles.stream().anyMatch(role -> role.equals("owner") || role.equals("admin"));
                default -> false;
            };
            if (!allowed) {
                throw new ApiErrorException(
                        HttpStatus.FORBIDDEN,
                        "capability-policy-blocked",
                        "This action is blocked by workspace role or group policy.",
                        Map.of("requiredCapability", capability, "policyState", "policy_blocked", "diagnosticsRedacted", true));
            }
            return null;
        }).when(workspaceCapabilityService).requireCapability(any(), anyString(), anyString(), anyString());
    }

    private void selectDefaultProviders() {
        if (providerSelectionRepository instanceof InMemoryProviderSelectionRepository memoryRepository) {
            memoryRepository.clear();
        }
        providerSelectionRepository.save(selection("identity-idm", "keycloak-realm", "recommended_self_hosted_default"));
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
    void adminControlPlaneRejectsMembers() throws Exception {
        mockMvc.perform(get("/api/admin/control-plane").with(memberJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
    }

    @Test
    void adminControlPlaneOverviewIsSupportSafeAndProviderNeutral() throws Exception {
        mockMvc.perform(get("/api/admin/control-plane").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("admin-control-plane-v1"))
                .andExpect(jsonPath("$.organizationId").value("weave-dogfood"))
                .andExpect(jsonPath("$.recommendedIdentityBroker").value("OIDC/SAML selected IDM"))
                .andExpect(jsonPath("$.providerConfigSource").value("admin-control-plane-selected-provider-mappings"))
                .andExpect(jsonPath("$.bootstrapDefaultsAreSuggestionsOnly").value(true))
                .andExpect(jsonPath("$.backendOwnedFacades").value(true))
                .andExpect(jsonPath("$.denyByDefaultPolicy").value(true))
                .andExpect(jsonPath("$.memberClientMayConfigureProviders").value(false))
                .andExpect(jsonPath("$.categories[*].category", hasItems(
                        "identity-idm", "chat", "files", "calendar", "boards-tasks", "meetings-calls", "documents-collaboration", "model", "weaver")))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].selectedByAdmin", hasItems(true)))
                .andExpect(jsonPath("$.categories[?(@.category == 'chat')].selectedProviderKey", hasItems("synapse-homeserver")))
                .andExpect(jsonPath("$.selectedProviderMappings[*].category", hasItems("identity-idm", "chat", "files")))
                .andExpect(jsonPath("$.selectedProviderMappings[*].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.weaverDistributionPolicy.modelAliases[0].provider").value("lmstudio"))
                .andExpect(jsonPath("$.weaverDistributionPolicy.modelAliases[0].model").value("lmstudio/qwen/qwen3.5-9b"))
                .andExpect(jsonPath("$.weaverDistributionPolicy.effectivePolicyPreview[*]", hasItems("credentialRef=credentialref://weave/channels/weave-chat/runtime-token")))
                .andExpect(jsonPath("$.weaverEligibilityPreview.policyEnabled").value(false))
                .andExpect(jsonPath("$.weaverEligibilityPreview.requiredGroups[*]", hasItems("weaver-group", "weave-weaver-runtime")))
                .andExpect(jsonPath("$.weaverEligibilityPreview.memberStateWithoutGroup").value("disabled_by_policy"))
                .andExpect(jsonPath("$.whitelist.denyByDefault").value(true))
                .andExpect(jsonPath("$.whitelist.normalMembersMayAuthorPolicy").value(false))
                .andExpect(jsonPath("$.whitelist.stableMemberImpactStates[*]", hasItems(
                        "available", "disabled_by_policy", "not_configured", "degraded", "unavailable", "coming_later")))
                .andExpect(jsonPath("$.whitelist.profileCapabilities['guest-deny-default']").isArray())
                .andExpect(jsonPath("$.identityProviderReadiness.contractVersion").value("identity-provider-readiness-v1"))
                .andExpect(jsonPath("$.identityProviderReadiness.backendOwnedFacade").value(true))
                .andExpect(jsonPath("$.identityProviderReadiness.memberClientMayConfigureIdentityProvider").value(false))
                .andExpect(jsonPath("$.identityProviderReadiness.stableStates[*]", hasItems("ready", "degraded", "policy-blocked", "admin-action-required", "coming_later", "disabled")))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[*].key", hasItems(
                        "realm-import", "federation-protocol-readiness", "provisioning-source-readiness",
                        "roles-groups-mapping", "login-readiness", "deprovisioning-readiness",
                        "break-glass-readiness", "service-principal-readiness", "policy-readiness")))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[?(@.key == 'provisioning-source-readiness')].diagnostics.scimConceptCovered", hasItems(true)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[?(@.key == 'provisioning-source-readiness')].diagnostics.liveLdapAdConnectorClaimed", hasItems(false)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[?(@.key == 'deprovisioning-readiness')].diagnostics.liveDestructiveMutationClaimed", hasItems(false)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[?(@.key == 'break-glass-readiness')].diagnostics.emailRecoveryKeyAllowed", hasItems(false)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[?(@.key == 'service-principal-readiness')].diagnostics.secretMaterialReturned", hasItems(false)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[*].diagnostics.secretsReturned", hasItems(false)))
                .andExpect(jsonPath("$.identityProviderReadiness.cards[*].diagnostics.rawProviderErrorsReturned", hasItems(false)))
                .andExpect(jsonPath("$.goLiveReadiness.releaseClaimControl.claimState").value("admin-action-required"))
                .andExpect(jsonPath("$.goLiveReadiness.releaseClaimControl.unresolvedVetoes[*]", hasItems("#591-manual-assistive-technology-signoff-open")))
                .andExpect(jsonPath("$.goLiveReadiness.releaseClaimControl.gates[*].key", hasItems("sprint-18-manual-at-signoff")))
                .andExpect(jsonPath("$.goLiveReadiness.releaseClaimControl.gates[?(@.key == 'sprint-18-manual-at-signoff')].blocksReleaseClaim", hasItems(true)))
                .andExpect(jsonPath("$.adminApiRoutes.identityReadiness").value("/api/admin/identity/readiness"))
                .andExpect(jsonPath("$.secretRefs[*].supportSafe", hasItems(true)))
                .andExpect(jsonPath("$.secretRefs[*].rawSecretExposed", hasItems(false)))
                .andExpect(jsonPath("$.adminApiRoutes.policy").value("/api/admin/policies/capability-whitelist"))
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))))
                .andExpect(content().string(not(containsString("weave-app"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))))
                .andExpect(content().string(not(containsString("access_token"))));
    }

    @Test
    void providerReplacementDryRunReturnsBackendOwnedPortableSwitchContract() throws Exception {
        String request = """
                {
                  "category": "identity-idm",
                  "currentAdapter": "keycloak-realm",
                  "targetAdapter": "authentik",
                  "choiceModel": "external_existing_provider",
                  "secretRef": "secretref://weave/provider/authentik",
                  "sourceOfTruth": "Admin Console-selected provider category remains Weave source of truth until apply.",
                  "lossyMappingNotes": ["support-safe preflight only"],
                  "portableExportImportRequired": true,
                  "requestedSwitchPlan": {
                    "plan": "guided-plan-preflight-export-import-cutover-rollback",
                    "memberFacingStateDuringSwitch": "degraded"
                  },
                  "reason": "dry-run with Bearer token-that-must-not-leak and client_secret"
                }
                """;

        mockMvc.perform(post("/api/admin/providers/replacements/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("identity-idm"))
                .andExpect(jsonPath("$.currentAdapter").value("keycloak-realm"))
                .andExpect(jsonPath("$.targetAdapter").value("authentik"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerDiagnosticsRedacted").value(true))
                .andExpect(jsonPath("$.lossyMappingReport.canonicalObjects[*]", hasItems("Subject", "Group", "CapabilityProfile")))
                .andExpect(jsonPath("$.lifecycleExpectations.sourceOfTruthPolicy", containsString("authoritative IdP")))
                .andExpect(jsonPath("$.portableExportImportContract.exportManifestRef").value("identity-idm-portable-export-manifest-v0.1"))
                .andExpect(jsonPath("$.portableExportImportContract.importManifestRef").value("identity-idm-portable-import-manifest-v0.1"))
                .andExpect(jsonPath("$.portableExportImportContract.portabilityGuarantee", containsString("documented portable export/import contract")))
                .andExpect(jsonPath("$.portableExportImportContract.excludedAutomation[*]", hasItems(containsString("full automated cross-provider migration"))))
                .andExpect(jsonPath("$.portableExportImportContract.evidenceRefs[*]", hasItems("provider-switch-preflight", "portable-export-import-contract", "rollback-recovery-plan")))
                .andExpect(jsonPath("$.crossDomainImpact[0].mappingClass").value("manual_review"))
                .andExpect(jsonPath("$.crossDomainImpact[0].evidenceRefs[*]", hasItems("identity-idm-portable-export-manifest-v0.1")))
                .andExpect(jsonPath("$.switchPlan.planRef").value("identity-idm-switch-plan-v0.1"))
                .andExpect(jsonPath("$.switchPlan.preflightRequired").value(true))
                .andExpect(jsonPath("$.switchPlan.cutoverWindowRequired").value(true))
                .andExpect(jsonPath("$.switchPlan.rollbackRequired").value(true))
                .andExpect(jsonPath("$.switchPlan.memberFacingStateDuringSwitch").value("degraded"))
                .andExpect(jsonPath("$.switchPlan.recoveryActions[*]", hasItems(containsString("keep current adapter active"), containsString("block apply"))))
                .andExpect(jsonPath("$.memberImpactStates[*]", hasItems("available", "disabled_by_policy", "degraded", "coming_later")))
                .andExpect(content().string(not(containsString("token-that-must-not-leak"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("secretref://"))));
    }

    @Test
    void matrixChatReplacementDryRunShowsCrossDomainImpactAndBlocksCutoverClaims() throws Exception {
        String request = """
                {
                  "category": "chat",
                  "currentAdapter": "synapse-homeserver",
                  "targetAdapter": "slack",
                  "choiceModel": "external_existing_provider",
                  "secretRef": "secretref://weave/provider/slack",
                  "sourceOfTruth": "Weave Chat remains source of truth until bounded evidence is accepted.",
                  "lossyMappingNotes": ["support-safe Chat impact review only"],
                  "portableExportImportRequired": true,
                  "reason": "cross-domain impact proof without raw provider diagnostics"
                }
                """;

        mockMvc.perform(post("/api/admin/providers/replacements/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("dry-run-blocked-for-apply"))
                .andExpect(jsonPath("$.boundedProof.productionCutoverAllowed").value(false))
                .andExpect(jsonPath("$.crossDomainImpact[*].domainKey", hasItems("chat", "files", "boards", "calendar", "decisions")))
                .andExpect(jsonPath("$.crossDomainImpact[*].mappingClass", hasItems("portable", "archive_only", "manual_review", "lossy", "unsupported", "vendor_locked")))
                .andExpect(jsonPath("$.crossDomainImpact[?(@.domainKey == 'files')].applyBlockers[0]", hasItems(containsString("rollback archive refs"))))
                .andExpect(jsonPath("$.noUnaccountedDataLossReport.releaseClaimBoundaries[*]", hasItems(containsString("No lossless migration"))))
                .andExpect(content().string(not(containsString("secretref://"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))));
    }

    @Test
    void identityProviderReadinessIsBackendOwnedAndMembersCannotReadIt() throws Exception {
        mockMvc.perform(get("/api/admin/identity/readiness").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractVersion").value("identity-provider-readiness-v1"))
                .andExpect(jsonPath("$.category").value("identity-idm"))
                .andExpect(jsonPath("$.overallState").value("admin-action-required"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerDiagnosticsRedacted").value(true))
                .andExpect(jsonPath("$.backendOwnedFacade").value(true))
                .andExpect(jsonPath("$.memberClientMayConfigureIdentityProvider").value(false))
                .andExpect(jsonPath("$.optionalForMemberFlows").value(true))
                .andExpect(jsonPath("$.cards[*].key", hasItems(
                        "realm-import", "federation-protocol-readiness", "provisioning-source-readiness",
                        "roles-groups-mapping", "login-readiness", "deprovisioning-readiness",
                        "break-glass-readiness", "service-principal-readiness", "policy-readiness")))
                .andExpect(jsonPath("$.cards[?(@.key == 'service-principal-readiness')].diagnostics.secretMaterialReturned", hasItems(false)))
                .andExpect(jsonPath("$.cards[*].state", hasItems("ready", "admin-action-required")))
                .andExpect(jsonPath("$.cards[*].remediation").isArray())
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))))
                .andExpect(content().string(not(containsString("weave-app"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))));

        mockMvc.perform(get("/api/admin/identity/readiness").with(memberJwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true))
                .andExpect(content().string(not(containsString("keycloak-realm"))))
                .andExpect(content().string(not(containsString("weave-app"))));
    }

    @Test
    void effectivePolicyExplainsAdminGrantsWithoutEmailAsPrimaryKey() throws Exception {
        mockMvc.perform(get("/api/admin/policies/effective").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("admin-123"))
                .andExpect(jsonPath("$.organization").value("weave-dogfood"))
                .andExpect(jsonPath("$.capabilityGrants[*]", hasItems("admin.policy.edit", "admin.provider.configure")))
                .andExpect(jsonPath("$.providerRoleMappings[*]", hasItems("role_claim:admin", "group_claim:weave-board-editors")))
                .andExpect(jsonPath("$.denies[0].capability").value("weaver.enabled"))
                .andExpect(jsonPath("$.denyByDefault").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.primaryIdentityKey", containsString("issuer+subject:")))
                .andExpect(jsonPath("$.emailPrimaryKey").value(false))
                .andExpect(content().string(not(containsString("alice@example.com"))));
    }


    @Test
    void effectivePolicySimulationIsAdminOperatorOnlySupportSafeAndAudited() throws Exception {
        String request = """
                {
                  "subject": "alice@example.com",
                  "organizationId": "weave-dogfood",
                  "roles": ["member"],
                  "groups": ["weave-board-editors"],
                  "requestedCapabilities": ["chat.send", "boards.update_task", "admin.provider.configure", "weaver.enabled"],
                  "reason": "preview before provider change with Bearer secret-token and client_secret"
                }
                """;

        mockMvc.perform(post("/api/admin/policies/effective/simulations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("identity-ref-redacted"))
                .andExpect(jsonPath("$.organizationId").value("weave-dogfood"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.unknownInputsFailClosed").value(false))
                .andExpect(jsonPath("$.weaverDefaultDisabled").value(true))
                .andExpect(jsonPath("$.grantedCapabilities[*]", hasItems("chat.send", "boards.update_task")))
                .andExpect(jsonPath("$.deniedInputs").isEmpty())
                .andExpect(jsonPath("$.capabilityStates[*].state", hasItems("ready", "disabled", "policy-blocked")))
                .andExpect(jsonPath("$.capabilityStates[?(@.capability == 'weaver.enabled')].reasonCode", hasItems("weaver-default-disabled")))
                .andExpect(jsonPath("$.capabilityStates[?(@.capability == 'admin.provider.configure')].state", hasItems("policy-blocked")))
                .andExpect(content().string(not(containsString("alice@example.com"))))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("keycloak-realm"))));

        mockMvc.perform(post("/api/admin/policies/effective/simulations")
                        .with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"member\"],\"requestedCapabilities\":[\"chat.read\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grantedCapabilities[*]", hasItems("chat.read")));

        mockMvc.perform(get("/api/admin/audit/events").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("effective_policy.simulated")))
                .andExpect(jsonPath("$[*].payload.roleCount", hasItems(1)))
                .andExpect(jsonPath("$[*].payload.groupCount", hasItems(1)))
                .andExpect(jsonPath("$[*].payload.requestedCapabilityCount", hasItems(4)))
                .andExpect(jsonPath("$[*].payload.supportSafe", hasItems(true)))
                .andExpect(jsonPath("$[*].payload.reasonProvided", hasItems(true)))
                .andExpect(content().string(not(containsString("preview before provider change"))))
                .andExpect(content().string(not(containsString("secret-token"))))
                .andExpect(content().string(not(containsString("alice@example.com"))));
    }

    @Test
    void memberCannotRunEffectivePolicySimulationOrSeePolicyInternals() throws Exception {
        mockMvc.perform(post("/api/admin/policies/effective/simulations")
                        .with(memberJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roles\":[\"admin\"],\"groups\":[\"weave-board-editors\"],\"requestedCapabilities\":[\"admin.provider.configure\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true))
                .andExpect(content().string(not(containsString("admin.provider.configure"))))
                .andExpect(content().string(not(containsString("weave-board-editors"))))
                .andExpect(content().string(not(containsString("keycloak-realm"))));
    }

    @Test
    void adminRealmDryRunIsBackendOwnedDeterministicAndSupportSafe() throws Exception {
        String request = """
                {
                  "desiredState": {
                    "realmId": "weave-dogfood",
                    "displayName": "Weave Dogfood",
                    "enabled": true,
                    "clients": [{"clientId":"weave-app","publicClient":true,"redirectOrigins":["https://weave.test/callback","http://localhost:8080/*"],"roles":["admin","member"],"scopes":["openid","profile"]}],
                    "roles": ["admin", "member"],
                    "groups": ["weave-board-editors"],
                    "scopes": ["openid", "profile", "weave:workspace"],
                    "claimMappers": [{"name":"tenant","sourceClaim":"weave_tenant","targetClaim":"organizationId","required":true}],
                    "redirectOrigins": ["http://localhost:8080/*"],
                    "featureMappings": [{"featureKey":"boards","requiredRoles":["member"],"requiredGroups":["weave-board-editors"],"requiredScopes":["openid"]}]
                  },
                  "reason": "admin review before apply"
                }
                """;

        mockMvc.perform(post("/api/admin/identity/realm/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("keycloak-realm"))
                .andExpect(jsonPath("$.operation").value("dry-run"))
                .andExpect(jsonPath("$.readiness").value("degraded"))
                .andExpect(jsonPath("$.destructiveApplyAvailable").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.rawSecretExposed").value(false))
                .andExpect(jsonPath("$.changes[*].classification", hasItems("safe", "risky")))
                .andExpect(jsonPath("$.readinessChecks[*].state", hasItems("ready", "degraded")))
                .andExpect(jsonPath("$.auditRefs[0]").exists())
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))))
                .andExpect(content().string(not(containsString("access_token"))));

        mockMvc.perform(get("/api/admin/audit/events").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("provider.replacement.dry_run")))
                .andExpect(jsonPath("$[*].payload.supportSafe", hasItems(true)))
                .andExpect(jsonPath("$[*].payload.dryRunReasonPresent", hasItems(true)))
                .andExpect(content().string(not(containsString("admin review before apply"))))
                .andExpect(content().string(not(containsString("server-test-secret-that-must-never-appear"))));
    }


    @Test
    void adminRealmApplyIsGuardedDecisionOnlySupportSafeAndAudited() throws Exception {
        String request = """
                {
                  "currentState": {
                    "realmId": "weave-dogfood",
                    "displayName": "Weave Dogfood",
                    "enabled": true,
                    "clients": [{"clientId":"weave-app","publicClient":true,"redirectOrigins":["https://weave.test/callback"],"roles":["owner","admin","member"],"scopes":["openid","profile","email"]}],
                    "roles": ["owner", "admin", "member"],
                    "groups": ["weave-board-editors"],
                    "scopes": ["openid", "profile", "email", "weave:workspace"],
                    "claimMappers": [{"name":"tenant","sourceClaim":"weave_tenant","targetClaim":"organizationId","required":true}],
                    "redirectOrigins": ["https://weave.test/callback"],
                    "featureMappings": [{"featureKey":"boards","requiredRoles":["member"],"requiredGroups":["weave-board-editors"],"requiredScopes":["openid"]}],
                    "breakGlassIdentities": [{"subjectRef":"issuer+subject:https://auth.example.invalid/realms/weave#admin-123","purpose":"last-admin recovery","breakGlass":true,"roles":["owner"]}],
                    "lastAdminSubjectRefs": ["issuer+subject:https://auth.example.invalid/realms/weave#admin-123"]
                  },
                  "desiredState": {
                    "realmId": "weave-dogfood",
                    "displayName": "Weave Dogfood",
                    "enabled": true,
                    "clients": [{"clientId":"weave-app","publicClient":true,"redirectOrigins":["https://weave.test/callback"],"roles":["owner","admin","member"],"scopes":["openid","profile","email"]}],
                    "roles": ["owner", "admin", "member"],
                    "groups": ["weave-board-editors"],
                    "scopes": ["openid", "profile", "email", "weave:workspace"],
                    "claimMappers": [{"name":"tenant","sourceClaim":"weave_tenant","targetClaim":"organizationId","required":true}],
                    "redirectOrigins": ["https://weave.test/callback"],
                    "featureMappings": [{"featureKey":"boards","requiredRoles":["member"],"requiredGroups":["weave-board-editors"],"requiredScopes":["openid"]}],
                    "breakGlassIdentities": [{"subjectRef":"issuer+subject:https://auth.example.invalid/realms/weave#admin-123","purpose":"last-admin recovery","breakGlass":true,"roles":["owner"]}],
                    "lastAdminSubjectRefs": ["issuer+subject:https://auth.example.invalid/realms/weave#admin-123"]
                  },
                  "confirmationPhrase": "APPLY WEAVE IDENTITY REALM",
                  "approveRisky": false,
                  "approveDestructive": false,
                  "retainedAdminPrimaryIdentityKeys": ["issuer+subject:https://auth.example.invalid/realms/weave#admin-123"],
                  "reason": "controller apply with Bearer raw-token and secretref://raw/ref"
                }
                """;

        String dryRunId = jsonField(mockMvc.perform(post("/api/admin/identity/realm/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn(), "dryRunId");
        String policySimulationRef = jsonArrayField(mockMvc.perform(post("/api/admin/policies/effective/simulations")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "subject": "issuer+subject:https://auth.example.invalid/realms/weave#member-123",
                                  "organizationId": "weave-dogfood",
                                  "roles": ["member"],
                                  "groups": ["weave-board-editors"],
                                  "requestedCapabilities": ["chat.read", "boards.update_task"],
                                  "reason": "support-safe policy simulation before realm apply"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn(), "auditRefs", 0);
        request = request.replace("\"confirmationPhrase\":", "\"dryRunId\": \"" + dryRunId + "\",\n                  \"policySimulationRef\": \"" + policySimulationRef + "\",\n                  \"confirmationPhrase\":");

        mockMvc.perform(post("/api/admin/identity/realm/apply")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("keycloak-realm"))
                .andExpect(jsonPath("$.decision").value("accepted"))
                .andExpect(jsonPath("$.executionMode").value("guarded-provider-live-apply-disabled"))
                .andExpect(jsonPath("$.applied").value(false))
                .andExpect(jsonPath("$.providerMutationPerformed").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.lastAdminGuardPassed").value(true))
                .andExpect(jsonPath("$.blockedReasons").isEmpty())
                .andExpect(jsonPath("$.auditRefs[0]").exists())
                .andExpect(content().string(not(containsString("raw-token"))))
                .andExpect(content().string(not(containsString("secretref://raw/ref"))));

        mockMvc.perform(get("/api/admin/audit/events").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].action", hasItems("identity.realm.apply.guarded")))
                .andExpect(jsonPath("$[*].payload.actorRef", hasItems("user:admin-123")))
                .andExpect(jsonPath("$[*].payload.decision", hasItems("accepted")))
                .andExpect(jsonPath("$[*].payload.result", hasItems("accepted-without-provider-mutation")))
                .andExpect(jsonPath("$[*].payload.providerMutationPerformed", hasItems(false)))
                .andExpect(content().string(not(containsString("raw-token"))))
                .andExpect(content().string(not(containsString("secretref://raw/ref"))))
                .andExpect(content().string(not(containsString("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"))));
    }

    @Test
    void operatorAndMemberCannotApplyIdentityRealmWhenPolicyForbidsProviderConfiguration() throws Exception {
        String request = "{\"desiredState\":{\"realmId\":\"weave-dogfood\"},\"confirmationPhrase\":\"APPLY WEAVE IDENTITY REALM\"}";

        mockMvc.perform(post("/api/admin/identity/realm/apply")
                        .with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin.provider.configure"));

        mockMvc.perform(post("/api/admin/identity/realm/apply")
                        .with(memberJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin.provider.configure"));
    }

    @Test
    void memberCannotRunRealmDryRunOrSeeProviderSetupInternals() throws Exception {
        mockMvc.perform(post("/api/admin/identity/realm/dry-run")
                        .with(memberJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredState\":{\"realmId\":\"weave\"}}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin_control_plane.readiness_read"))
                .andExpect(content().string(not(containsString("keycloak-realm"))))
                .andExpect(content().string(not(containsString("client_secret"))))
                .andExpect(content().string(not(containsString("provider setup"))));
    }

    @Test
    void operatorCanReadButCannotChangeProviderOrPolicy() throws Exception {
        mockMvc.perform(get("/api/admin/control-plane").with(operatorJwt()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/identity/realm/dry-run")
                        .with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"desiredState\":{\"realmId\":\"weave-dogfood\",\"clients\":[],\"roles\":[\"admin\"]}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operation").value("dry-run"));

        mockMvc.perform(patch("/api/admin/policies/capability-whitelist")
                        .with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileKey\":\"workspace-admin\",\"capabilityKeys\":[\"admin.policy.edit\"]}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(operatorJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"chat\",\"providerKey\":\"slack\",\"choiceModel\":\"external_existing_provider\",\"secretRef\":\"secretref://weave/provider/slack\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminsCanBootstrapExistingAndNewOrganizationsWithLastAdminRecoveryKey() throws Exception {
        mockMvc.perform(post("/api/admin/organizations/bootstrap")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"acme-prod\",\"bootstrapMode\":\"existing_org\",\"adminPrimaryIdentityKeys\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("acme-prod"))
                .andExpect(jsonPath("$.bootstrapMode").value("existing_org"))
                .andExpect(jsonPath("$.actorPrimaryIdentityKey").value("issuer+subject:https://auth.example.invalid/realms/weave#admin-123"))
                .andExpect(jsonPath("$.retainedAdminPrimaryIdentityKeys[*]", hasItems("issuer+subject:https://auth.example.invalid/realms/weave#admin-123")))
                .andExpect(jsonPath("$.lastAdminGuardPassed").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true));

        mockMvc.perform(post("/api/admin/organizations/bootstrap")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"newco\",\"bootstrapMode\":\"new_org\",\"adminPrimaryIdentityKeys\":[\"issuer+subject:https://auth.example.invalid/realms/weave#admin-123\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organizationId").value("newco"))
                .andExpect(jsonPath("$.bootstrapMode").value("new_org"))
                .andExpect(jsonPath("$.emailPrimaryKey").doesNotExist());
    }

    @Test
    void bootstrapRejectsUnsafeAdminRecoveryKeys() throws Exception {
        mockMvc.perform(post("/api/admin/organizations/bootstrap")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"acme-prod\",\"bootstrapMode\":\"existing_org\",\"adminPrimaryIdentityKeys\":[\"alice@example.com\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(content().string(not(containsString("alice@example.com"))))
                .andExpect(content().string(containsString("adminPrimaryIdentityKeys")));
    }

    @Test
    void bootstrapRejectsNullAdminRecoveryKeysAsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/organizations/bootstrap")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"organizationId\":\"acme-prod\",\"bootstrapMode\":\"existing_org\",\"adminPrimaryIdentityKeys\":[null]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(content().string(containsString("adminPrimaryIdentityKeys")));
    }

    @Test
    void lastAdminGuardRejectsWorkspaceAdminPolicyThatRemovesPolicyEdit() throws Exception {
        mockMvc.perform(patch("/api/admin/policies/capability-whitelist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileKey\":\"Workspace-Admin\",\"capabilityKeys\":[\"chat.read\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("last-admin-guard"))
                .andExpect(content().string(not(containsString("alice@example.com"))));
    }

    @Test
    void lastAdminGuardRejectsEmptyWorkspaceAdminPolicy() throws Exception {
        mockMvc.perform(patch("/api/admin/policies/capability-whitelist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileKey\":\"workspace-admin\",\"capabilityKeys\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("last-admin-guard"));
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

        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"model\",\"providerKey\":\"lmstudio\",\"choiceModel\":\"recommended_self_hosted_default\",\"secretRef\":\"secretref://weave/provider/lmstudio\",\"reason\":\"route Weaver chat through local LM Studio\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("model"))
                .andExpect(jsonPath("$.providerKey").value("lmstudio"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.secretRef").value("secretref://weave/provider/lmstudio"));

        mockMvc.perform(post("/api/admin/providers/readiness-tests")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerKey\":\"slack\",\"testKind\":\"readiness\",\"secretRef\":\"secretref://weave/provider/slack\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.providerKey").value("slack"))
                .andExpect(jsonPath("$.auditEventPublished").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.rawSecretExposed").value(false))
                .andExpect(jsonPath("$.diagnostics.backendAdapterKey").value("synapse-homeserver"))
                .andExpect(jsonPath("$.diagnostics.secretsReturned").value(false));

        mockMvc.perform(patch("/api/admin/policies/capability-whitelist")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileKey\":\"workspace-admin\",\"capabilityKeys\":[\"admin.policy.edit\",\"chat.read\",\"calendar.manage_events\"],\"reason\":\"grant through Admin Console\"}"))
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

    @Test
    void weaverSelectionUsesContractCandidatesEvenWithoutRawProviderPort() throws Exception {
        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"weaver\",\"providerKey\":\"openclaw-derived-profile\",\"choiceModel\":\"managed_cloud_provider\",\"secretRef\":\"secretref://weave/provider/openclaw-derived-profile\",\"reason\":\"governed runtime pilot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("weaver"))
                .andExpect(jsonPath("$.providerKey").value("openclaw-derived-profile"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.migrationDryRunRequired").value(true));

        mockMvc.perform(get("/api/admin/control-plane").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categories[?(@.category == 'weaver')].selectedByAdmin", hasItems(true)))
                .andExpect(jsonPath("$.categories[?(@.category == 'weaver')].selectedProviderKey", hasItems("openclaw-derived-profile")))
                .andExpect(jsonPath("$.categories[?(@.category == 'weaver')].providerCandidates[*]", hasItems("openclaw-derived-profile")))
                .andExpect(content().string(not(containsString("raw provider"))));
    }

    @Test
    void memberCannotChangeWeaverProviderSelection() throws Exception {
        mockMvc.perform(post("/api/admin/providers/selections")
                        .with(memberJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\":\"weaver\",\"providerKey\":\"openclaw-derived-profile\",\"choiceModel\":\"recommended_self_hosted_default\",\"secretRef\":\"secretref://weave/provider/openclaw-derived-profile\",\"reason\":\"member attempt\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability").value("admin.provider.configure"))
                .andExpect(jsonPath("$.details.diagnosticsRedacted").value(true));
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

    private String jsonField(MvcResult result, String fieldName) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path(fieldName).asText();
    }

    private String jsonArrayField(MvcResult result, String fieldName, int index) throws Exception {
        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        return root.path(fieldName).path(index).asText();
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("admin-123")
                        .claim("iss", "https://auth.example.invalid/realms/weave")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant", "weave-dogfood")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("admin"))))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor operatorJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("operator-123")
                        .claim("iss", "https://auth.example.invalid/realms/weave")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant", "weave-dogfood")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("operator"))))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_OPERATOR"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor memberJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/weave")
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

        @Bean
        OrganizationBootstrapRepository organizationBootstrapRepository() {
            return new InMemoryOrganizationBootstrapRepository();
        }
    }
}
