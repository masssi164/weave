package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceReleaseReadinessService;
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
        WorkspaceReleaseReadinessService.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        WeaveSecurityProperties.class,
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

    @MockBean
    private JwtDecoder jwtDecoder;

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
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/workspace/capabilities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/capabilities"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/workspace/release-readiness"))
                .andExpect(status().isUnauthorized());
    }

    private void assertConfiguredWorkspaceCapabilities(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shellAccess.enabled").value(true))
                .andExpect(jsonPath("$.shellAccess.readiness").value("ready"))
                .andExpect(jsonPath("$.chat.readiness").value("ready"))
                .andExpect(jsonPath("$.files.readiness").value("ready"))
                .andExpect(jsonPath("$.calendar.enabled").value(true))
                .andExpect(jsonPath("$.calendar.readiness").value("degraded"))
                .andExpect(jsonPath("$.boards.readiness").value("unavailable"));
    }

    private void assertReleaseReadinessSnapshot(String path) throws Exception {
        mockMvc.perform(get(path).with(jwt()
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readiness").value("ready"))
                .andExpect(jsonPath("$.checks[0].key").value("auth-contract"))
                .andExpect(jsonPath("$.checks[1].key").value("chat"))
                .andExpect(jsonPath("$.checks[2].key").value("files"))
                .andExpect(jsonPath("$.actions").isEmpty());
    }
}
