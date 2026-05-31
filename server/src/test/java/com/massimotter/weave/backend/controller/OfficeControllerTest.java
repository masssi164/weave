package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.service.OfficeFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OfficeController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        DisabledOfficeProvider.class,
        OfficeFacadeService.class,
        WorkspaceCapabilityService.class
})
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave")
@EnableConfigurationProperties({
        WorkspaceCapabilityProperties.class,
        WeaveSecurityProperties.class,
        OAuth2ResourceServerProperties.class
})
class OfficeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void officeCapabilitiesRequireWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/office/capabilities"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void officeCapabilitiesDoNotPromiseEditingWhenUnavailable() throws Exception {
        mockMvc.perform(get("/api/office/capabilities").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.launchMode").value("unavailable"))
                .andExpect(jsonPath("$.defaultProvider").value("onlyoffice-community"))
                .andExpect(jsonPath("$.capabilities.view").value(false))
                .andExpect(jsonPath("$.capabilities.edit").value(false))
                .andExpect(jsonPath("$.capabilities.comment").value(false))
                .andExpect(jsonPath("$.capabilities.review").value(false))
                .andExpect(jsonPath("$.capabilities.formFill").value(false))
                .andExpect(jsonPath("$.candidates[*].providerKey", hasItems("onlyoffice-community", "collabora-code")))
                .andExpect(jsonPath("$.providerReadiness[0].unsupportedOperations", hasItems("launch-session")))
                .andExpect(jsonPath("$.providerReadiness[0].diagnostics").isEmpty())
                .andExpect(jsonPath("$.providerReadiness[0].diagnostics.providerRealityLevel").doesNotExist())
                .andExpect(jsonPath("$.providerReadiness[0].diagnostics.memberImpact").doesNotExist())
                .andExpect(jsonPath("$.providerReadiness[0].diagnostics.missingReadinessPrerequisites").doesNotExist())
                .andExpect(content().string(not(containsString("document-runtime"))))
                .andExpect(content().string(not(containsString("jwt-or-session-secret"))));
    }

    @Test
    void officeLaunchFailsClosedWithoutLeakingProviderSecrets() throws Exception {
        mockMvc.perform(post("/api/office/launch")
                        .with(workspaceJwt())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileId\":\"file-123\",\"requestedMode\":\"edit\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("office-provider-not-configured"))
                .andExpect(jsonPath("$.details.module").value("office"))
                .andExpect(jsonPath("$.details.operation").value("launch"))
                .andExpect(jsonPath("$.details.supportSafe").value(true))
                .andExpect(content().string(not(containsString("password"))))
                .andExpect(content().string(not(containsString("Authorization"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/weave")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member")))
                        .claim("groups", java.util.List.of("weave-document-editors")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
