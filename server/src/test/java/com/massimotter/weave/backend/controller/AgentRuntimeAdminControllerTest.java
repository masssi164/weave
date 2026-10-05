package com.massimotter.weave.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService;
import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeAdminService.AdminContext;
import com.massimotter.weave.backend.agentruntime.port.RuntimeCommandConflictException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePersonNotFoundException;
import com.massimotter.weave.backend.agentruntime.port.RuntimePolicyException;
import com.massimotter.weave.backend.config.AgentRuntimeAdminSecurityConfiguration;
import com.massimotter.weave.backend.config.AgentRuntimeErrorResponseWriter;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.exception.AgentRuntimeAdminExceptionHandler;
import com.massimotter.weave.backend.model.agentruntime.AgentRuntimeProjectionResponse;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.security.DeploymentOrganizationAdmission;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(
        controllers = AgentRuntimeAdminController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        OrganizationIdentityContextResolver.class,
        DeploymentOrganizationAdmission.class,
        AgentRuntimeAdminSecurityConfiguration.class,
        AgentRuntimeErrorResponseWriter.class,
        ApiErrorResponseWriter.class,
        AgentRuntimeAdminExceptionHandler.class
})
@TestPropertySource(properties = {
        "weave.agent-runtime.workload-identity.enabled=true",
        "weave.agent-runtime.policy.enabled=true",
        "weave.agent-runtime.profile-signing.enabled=true",
        "weave.agent-runtime.state-store.enabled=true"
})
class AgentRuntimeAdminControllerTest {
    // V01_AGENT_RUNTIME_CONTROL_POLICY
    private static final String PERSON = "acct_" + "a".repeat(32);
    private static final String KEY = "idempotency-key-00000001";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AgentRuntimeAdminService runtimes;

    @MockitoBean(name = "agentRuntimeAdminJwtDecoder")
    private JwtDecoder agentRuntimeAdminJwtDecoder;

    @ParameterizedTest
    @ValueSource(strings = {"owner", "admin"})
    void configuredOrganizationAdminBearerUsesTheRuntimeScope(String role) throws Exception {
        given(agentRuntimeAdminJwtDecoder.decode("current-org"))
                .willReturn(adminBearer(role, "current"));
        given(runtimes.get(any(AdminContext.class), eq(PERSON))).willReturn(projection());

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON)
                        .header("Authorization", "Bearer current-org"))
                .andExpect(status().isOk());
        ArgumentCaptor<AdminContext> context = ArgumentCaptor.forClass(AdminContext.class);
        verify(runtimes).get(context.capture(), eq(PERSON));
        assertThat(context.getValue().organizationRef()).isEqualTo("tenant-default");
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-id", "wrong-alias", "missing", "multiple", "tenant", "fallback",
            "member", "mixed-roles", "missing-scope"})
    void foreignOrAmbiguousHumanContextCannotReadOrMutateRuntimes(String mismatch) throws Exception {
        given(agentRuntimeAdminJwtDecoder.decode("denied-context"))
                .willReturn(adminBearer("admin", mismatch));

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON)
                        .header("Authorization", "Bearer denied-context"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent-runtime-admin-forbidden"))
                .andExpect(jsonPath("$.details").doesNotExist());
        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/provision", PERSON)
                        .header("Authorization", "Bearer denied-context")
                        .header("Idempotency-Key", KEY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(runtimes);
    }

    @Test
    void exactAdminScopeAndOrganizationBoundIdentityAreRequired() throws Exception {
        given(runtimes.get(any(AdminContext.class), eq(PERSON))).willReturn(projection());

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("agent-runtime-admin-unauthorized"))
                .andExpect(jsonPath("$.capabilityState").value("unavailable"))
                .andExpect(jsonPath("$.retryable").value(false))
                .andExpect(jsonPath("$.auditRef").value(org.hamcrest.Matchers.startsWith("audit:arc:")))
                .andExpect(jsonPath("$.details").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON)
                        .with(jwt().authorities()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent-runtime-admin-forbidden"));

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON)
                        .with(jwt().authorities(new SimpleGrantedAuthority(
                                AgentRuntimeAdminSecurityConfiguration.ADMIN_AUTHORITY))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("agent-runtime-admin-forbidden"));

        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON)
                        .with(adminJwt()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.personRef").value(PERSON));

        ArgumentCaptor<AdminContext> context = ArgumentCaptor.forClass(AdminContext.class);
        verify(runtimes).get(context.capture(), eq(PERSON));
        assertThat(context.getValue().organizationRef()).isEqualTo("tenant-default");
        assertThat(context.getValue().actorRef())
                .isEqualTo("issuer+subject:https://auth.weave.test/realms/weave#admin-user-1");
        assertThat(context.getValue().auditRef()).startsWith("audit:arc:");
    }

    @Test
    void lifecycleRoutesReturnAcceptedAndRejectNonCanonicalInput() throws Exception {
        given(runtimes.provision(any(AdminContext.class), eq(PERSON), eq(KEY)))
                .willReturn(projection());
        given(runtimes.stop(any(AdminContext.class), eq(PERSON), eq(KEY), any()))
                .willReturn(projection());
        given(runtimes.deleteRuntimeState(any(AdminContext.class), eq(PERSON), eq(KEY), eq("retention")))
                .willReturn(projection());

        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/provision", PERSON)
                        .header("Idempotency-Key", KEY)
                        .with(adminJwt()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Cache-Control", "no-store"));

        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/provision", PERSON)
                        .header("Idempotency-Key", "too-short")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("agent-runtime-invalid-request"));

        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/stop", PERSON)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"force-after-timeout\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/stop", PERSON)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"graceful\",\"providerUrl\":\"https://private.example\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.providerUrl").doesNotExist());

        mockMvc.perform(delete("/api/admin/agent-runtimes/{personRef}/runtime-state", PERSON)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retention\",\"confirmation\":\"DELETE_RUNTIME_STATE_ONLY\"}")
                        .with(adminJwt()))
                .andExpect(status().isAccepted());

        mockMvc.perform(delete("/api/admin/agent-runtimes/{personRef}/runtime-state", PERSON)
                        .header("Idempotency-Key", KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"retention\",\"confirmation\":\"DELETE_ALL_FILES\"}")
                        .with(adminJwt()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void boundedContextFailuresUseOnlyTheClosedSupportSafeEnvelope() throws Exception {
        given(runtimes.get(any(AdminContext.class), eq(PERSON)))
                .willThrow(new RuntimePersonNotFoundException("raw member detail"));
        mockMvc.perform(get("/api/admin/agent-runtimes/{personRef}", PERSON).with(adminJwt()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("agent-runtime-not-found"))
                .andExpect(jsonPath("$.userMessage").value("The requested Agent Runtime is unavailable."))
                .andExpect(jsonPath("$.*", org.hamcrest.Matchers.hasSize(5)));

        given(runtimes.provision(any(AdminContext.class), eq(PERSON), eq(KEY)))
                .willThrow(new RuntimeCommandConflictException("raw stale database value"));
        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/provision", PERSON)
                        .header("Idempotency-Key", KEY).with(adminJwt()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("agent-runtime-conflict"))
                .andExpect(jsonPath("$.userMessage", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("database"))));

        given(runtimes.provision(any(AdminContext.class), eq(PERSON), eq("idempotency-key-00000002")))
                .willThrow(new RuntimePolicyException("https://private.example secret token"));
        mockMvc.perform(post("/api/admin/agent-runtimes/{personRef}/provision", PERSON)
                        .header("Idempotency-Key", "idempotency-key-00000002").with(adminJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("agent-runtime-dependency-unavailable"))
                .andExpect(jsonPath("$.retryable").value(true))
                .andExpect(jsonPath("$.userMessage", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("private.example"))));
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor
            adminJwt() {
        return jwt()
                .jwt(token -> token
                        .issuer("https://auth.weave.test/realms/weave")
                        .subject("admin-user-1")
                        .issuedAt(Instant.parse("2026-07-20T10:00:00Z"))
                        .expiresAt(Instant.parse("2026-07-20T10:05:00Z"))
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("admin"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("scope", AgentRuntimeAdminSecurityConfiguration.ADMIN_SCOPE))
                .authorities(new SimpleGrantedAuthority(
                                AgentRuntimeAdminSecurityConfiguration.ADMIN_AUTHORITY),
                        new SimpleGrantedAuthority(
                                AgentRuntimeAdminSecurityConfiguration.ADMIN_ROLE_AUTHORITY));
    }

    private static Jwt adminBearer(String role, String mismatch) {
        var selected = new HashMap<String, Object>();
        selected.put("id", "wrong-id".equals(mismatch) ? "foreign-native-org" : HumanJwtTestSupport.ORGANIZATION_ID);
        List<String> roles = switch (mismatch) {
            case "member" -> List.of("member");
            case "mixed-roles" -> List.of("admin", "member");
            default -> List.of(role);
        };
        selected.put("resource_access", Map.of("weave-app", Map.of("roles", roles)));
        var organizations = new HashMap<String, Object>();
        organizations.put("wrong-alias".equals(mismatch) ? "foreign-alias" : HumanJwtTestSupport.ORGANIZATION_ALIAS,
                selected);
        if ("multiple".equals(mismatch)) organizations.put("another-org", selected);
        var builder = Jwt.withTokenValue("admin-bearer").header("alg", "none")
                .issuer("https://auth.weave.test/realms/weave").subject("admin-user-1")
                .audience(List.of("https://api.weave.test/api"))
                .claim("azp", "weave-admin-console")
                .claim("scope", "missing-scope".equals(mismatch) ? "weave:workspace"
                        : AgentRuntimeAdminSecurityConfiguration.ADMIN_SCOPE);
        if (!"missing".equals(mismatch)) builder.claim("organization", organizations);
        if ("tenant".equals(mismatch)) builder.claim("weave_tenant_id", "foreign-tenant");
        if ("fallback".equals(mismatch)) builder.claim("tenant_id", "foreign-tenant");
        return builder.build();
    }

    private static AgentRuntimeProjectionResponse projection() {
        return new AgentRuntimeProjectionResponse(
                PERSON, "cell:example", "weaver-openclaw", "entitled",
                "sha256:" + "1".repeat(64), "stopped", "absent", null,
                "workspace:v1", null, null, 0, "not_configured", "audit:projection");
    }
}
