package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.admin.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.service.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import java.time.Instant;
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
import org.springframework.test.web.servlet.MockMvc;

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
        controllers = ProviderCapabilityHealthController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
class ProviderCapabilityHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProviderCapabilityHealthService providerHealthService;

    @MockitoBean
    private WorkspaceCapabilityService workspaceCapabilityService;

    @BeforeEach
    void setUp() {
        when(providerHealthService.supportSafeSnapshot()).thenReturn(new ProviderCapabilityHealthResponse(
                "provider-capability-health-v1",
                Instant.parse("2026-07-12T08:00:00Z"),
                true,
                List.of(new ProviderCapabilityHealthResponse.CapabilityHealth(
                        "files",
                        "degraded",
                        "files-storage-rate-limited",
                        "provider-health:files:57bd6f0b-29c9-4df4-890f-fefbb2b5e6ba",
                        Instant.parse("2026-07-12T07:59:00Z"),
                        Instant.parse("2026-07-12T08:02:00Z"),
                        Instant.parse("2026-07-12T08:02:00Z"),
                        60L,
                        false,
                        1,
                        42,
                        2))));
        doAnswer(invocation -> {
            org.springframework.security.oauth2.jwt.Jwt token = invocation.getArgument(0);
            List<String> roles = token == null
                    ? List.of()
                    : ((Map<String, Object>) token.getClaimAsMap("resource_access").get("weave-app")).get("roles") instanceof List<?> values
                            ? values.stream().filter(String.class::isInstance).map(String.class::cast).toList()
                            : List.of();
            if (roles.stream().noneMatch(role -> role.equals("owner") || role.equals("admin") || role.equals("operator"))) {
                throw new ApiErrorException(
                        HttpStatus.FORBIDDEN,
                        "capability-policy-blocked",
                        "This action is blocked by workspace role or group policy.",
                        Map.of(
                                "requiredCapability", "admin_control_plane.readiness_read",
                                "policyState", "policy_blocked",
                                "diagnosticsRedacted", true));
            }
            return null;
        }).when(workspaceCapabilityService).requireCapability(any(), anyString(), anyString(), anyString());
    }

    @Test
    void cachedHealthIsNeverPublic() throws Exception {
        mockMvc.perform(get("/api/admin/provider-capability-health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void membersCannotReadOperatorHealthEvidence() throws Exception {
        mockMvc.perform(get("/api/admin/provider-capability-health").with(workspaceJwt("member")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("capability-policy-blocked"))
                .andExpect(jsonPath("$.details.requiredCapability")
                        .value("admin_control_plane.readiness_read"));
    }

    @Test
    void operatorsCanExportOnlyTheCachedSupportSafeSnapshot() throws Exception {
        mockMvc.perform(get("/api/admin/provider-capability-health").with(workspaceJwt("operator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaVersion").value("provider-capability-health-v1"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.capabilities[0].capability").value("files"))
                .andExpect(jsonPath("$.capabilities[0].state").value("degraded"))
                .andExpect(jsonPath("$.capabilities[0].supportSafeCode")
                        .value("files-storage-rate-limited"))
                .andExpect(jsonPath("$.capabilities[0].consecutiveFailures").value(1))
                .andExpect(jsonPath("$.capabilities[0].backoffUntil").exists())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Nextcloud"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("WebDAV"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt(String role) {
        return jwt()
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))
                .jwt(token -> token
                        .subject("support-user")
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of(role)))));
    }
}
