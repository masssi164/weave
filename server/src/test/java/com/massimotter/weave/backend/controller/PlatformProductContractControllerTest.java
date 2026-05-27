package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.context.authz.ContextAuthorizationDecision;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave",
        "weave.interop.slack.token-ref=secret://slack/bot-token",
        "weave.interop.slack.signing-secret-ref=secret://slack/signing-secret",
        "weave.interop.slack.client-secret-ref=secret://slack/client-secret"
})
@AutoConfigureMockMvc
class PlatformProductContractControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ContextAuthorizationPort contextAuthorizationPort;

    @BeforeEach
    void allowContextAccess() {
        when(contextAuthorizationPort.check(any()))
                .thenReturn(ContextAuthorizationDecision.allow("test allow"));
    }

    @Test
    void interopGatewayIsDisabledByDefaultAndRedactsProviderSecretReferences() throws Exception {
        mockMvc.perform(get("/api/interop/status").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.readiness").value("unavailable"))
                .andExpect(jsonPath("$.connections[?(@.provider == 'slack')].status").value("disabled"))
                .andExpect(jsonPath("$.supportBundle.providerSecretsRedacted").value(true))
                .andExpect(content().string(not(containsString("secret://slack"))));

        mockMvc.perform(post("/api/interop/slack/oauth/callback")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"oauth-code\",\"state\":\"opaque\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("interop-gateway-disabled"))
                .andExpect(jsonPath("$.details.productionCallsAllowed").value(false));
    }

    @Test
    void teamsAndConnectorContractsStayExplicitlyGated() throws Exception {
        mockMvc.perform(get("/api/interop/teams/contract").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatedBehindSlackHardening").value(true))
                .andExpect(jsonPath("$.status").value("gated"));

        mockMvc.perform(get("/api/connectors/boundary").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicSdkEnabled").value(false))
                .andExpect(jsonPath("$.status").value("deferred"));

        mockMvc.perform(post("/api/connectors/manifest/validate")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "id": "slack-proof",
                                  "provider": "slack",
                                  "capabilities": ["chat.message.read"],
                                  "secretRefs": {"botToken": "xoxb-leaked-token"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.secretValuesAccepted").value(false))
                .andExpect(jsonPath("$.errors", hasItem(containsString("secret material"))));
    }

    @Test
    void guestIdentityContractSeparatesGuestsAndDeniesAccessWhenDisabled() throws Exception {
        mockMvc.perform(get("/api/guest/access-contract").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.identityType").value("guest"))
                .andExpect(jsonPath("$.silentlyMergedWithInternalUsers").value(false))
                .andExpect(jsonPath("$.externalIdentityLinkingAudited").value(true));

        mockMvc.perform(post("/api/guest/invitations")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"guest@example.invalid\",\"capabilities\":[\"file.read\"]}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("guest-access-disabled"))
                .andExpect(jsonPath("$.details.defaultAccess").value("deny"));
    }

    @Test
    void migrationDryRunIsReplaySafeAndReportsConsentAndBudget() throws Exception {
        String request = """
                {
                  "sourceProvider": "slack",
                  "inventory": {
                    "workspaces": 1,
                    "channels": 2,
                    "users": 5,
                    "files": 20,
                    "messages": 400,
                    "scopes": ["channels:read"]
                  }
                }
                """;

        MvcResult first = mockMvc.perform(post("/api/migration/dry-runs")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.mode").value("dry-run"))
                .andExpect(jsonPath("$.replaySafe").value(true))
                .andExpect(jsonPath("$.mappingProposal.weaveRooms").value(2))
                .andExpect(jsonPath("$.consentRequirements.adminConsentRequired").value(true))
                .andExpect(jsonPath("$.rateLimitBudget.degradedStates[0]").value("rate_limited"))
                .andReturn();

        MvcResult second = mockMvc.perform(post("/api/migration/dry-runs")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();

        String firstJob = com.jayway.jsonpath.JsonPath.read(first.getResponse().getContentAsString(), "$.jobId");
        String secondJob = com.jayway.jsonpath.JsonPath.read(second.getResponse().getContentAsString(), "$.jobId");
        org.assertj.core.api.Assertions.assertThat(secondJob).isEqualTo(firstJob);
    }

    @Test
    void providerReplacementDryRunReportsAntiSiloEvidenceAndRedactsDiagnostics() throws Exception {
        mockMvc.perform(post("/api/admin/providers/replacements/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "chat",
                                  "currentAdapter": "synapse-homeserver",
                                  "targetAdapter": "slack",
                                  "choiceModel": "external_existing_provider",
                                  "secretRef": "secretref://weave/provider/slack",
                                  "sourceOfTruth": "selected chat provider owns message history",
                                  "lossyMappingNotes": ["Slack rich cards need review", "Bearer raw-token is redacted", "https://tenant.example.invalid/private is redacted"],
                                  "reason": "evaluate provider swap before activation"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("dry-run"))
                .andExpect(jsonPath("$.status").value("dry-run-ready"))
                .andExpect(jsonPath("$.category").value("chat"))
                .andExpect(jsonPath("$.currentAdapter").value("synapse-homeserver"))
                .andExpect(jsonPath("$.targetAdapter").value("slack"))
                .andExpect(jsonPath("$.secretRefPresent").value(true))
                .andExpect(jsonPath("$.migrationDryRunRequired").value(true))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerDiagnosticsRedacted").value(true))
                .andExpect(jsonPath("$.lossyMappingReport.canonicalObjects", hasItem("Message")))
                .andExpect(jsonPath("$.lossyMappingReport.contractRisks", hasItem(containsString("Slack"))))
                .andExpect(jsonPath("$.lifecycleExpectations.exportExpectation", containsString("export")))
                .andExpect(jsonPath("$.lifecycleExpectations.deprovisionExpectation", containsString("deprovision")))
                .andExpect(jsonPath("$.memberImpactStates", hasItem("policy-blocked")))
                .andExpect(jsonPath("$.auditRefs[0]", containsString("provider-replacement-dry-run-chat")))
                .andExpect(content().string(not(containsString("raw-token"))))
                .andExpect(content().string(not(containsString("tenant.example.invalid"))))
                .andExpect(content().string(not(containsString("Authorization: Bearer"))));

        mockMvc.perform(get("/api/admin/audit/events").with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("provider.replacement.dry_run")))
                .andExpect(content().string(not(containsString("raw-token"))))
                .andExpect(content().string(not(containsString("tenant.example.invalid"))));
    }

    @Test
    void providerReplacementDryRunRejectsRawSecretsAndUnsupportedAdapterCombinations() throws Exception {
        mockMvc.perform(post("/api/admin/providers/replacements/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "chat",
                                  "currentAdapter": "synapse-homeserver",
                                  "targetAdapter": "slack",
                                  "choiceModel": "external_existing_provider",
                                  "secretRef": "xoxb-raw-token",
                                  "sourceOfTruth": "selected chat provider owns message history"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("provider-replacement-secretref-invalid"))
                .andExpect(jsonPath("$.details.secretRef").value("invalid-secret-ref-redacted"))
                .andExpect(content().string(not(containsString("xoxb-raw-token"))));

        mockMvc.perform(post("/api/admin/providers/replacements/dry-run")
                        .with(adminJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category": "chat",
                                  "currentAdapter": "synapse-homeserver",
                                  "targetAdapter": "sharepoint",
                                  "choiceModel": "external_existing_provider",
                                  "secretRef": "secretref://weave/provider/sharepoint",
                                  "sourceOfTruth": "selected chat provider owns message history"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("provider-replacement-category-mismatch"))
                .andExpect(content().string(not(containsString("sharepoint.example"))));
    }

    @Test
    void calendarAccessPolicyAndSetupCredentialsAreRevocableWithoutSecretOutput() throws Exception {
        mockMvc.perform(get("/api/calendar/access-policy").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendActorMayReadPrivateUserCalendars").value(false))
                .andExpect(jsonPath("$.deniedScopes[0]").value("private-personal-calendar.read"));

        MvcResult created = mockMvc.perform(post("/api/calendar/client-setup/credentials")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"label\":\"iPhone\",\"clientType\":\"apple-mobileconfig\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("active-no-secret-issued"))
                .andExpect(jsonPath("$.secretMaterialReturned").value(false))
                .andExpect(jsonPath("$.profilePasswordEligible").value(false))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("bearer"))))
                .andReturn();

        String credentialId = com.jayway.jsonpath.JsonPath.read(created.getResponse().getContentAsString(), "$.credentialId");
        mockMvc.perform(get("/api/calendar/client-setup/credentials").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentials[0].credentialId").value(credentialId));

        mockMvc.perform(delete("/api/calendar/client-setup/credentials/{credentialId}", credentialId)
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("revoked"))
                .andExpect(jsonPath("$.secretMaterialReturned").value(false));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "test")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("aud", java.util.List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("admin-123")
                        .issuer("https://auth.example.invalid/realms/weave")
                        .claim("preferred_username", "admin")
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("admin"))))
                .authorities(
                        new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                        new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
