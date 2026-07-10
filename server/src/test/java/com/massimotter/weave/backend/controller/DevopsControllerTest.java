package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.service.DevopsFacadeService;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = DevopsController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        DevopsProviderConfiguration.class,
        DevopsFacadeService.class
})
@TestPropertySource(properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave")
class DevopsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void devopsSummaryRequiresWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/workspaces/workspace-default/channels/channel-general/devops/summary"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void devopsSummaryFailsClosedWithReadOnlyNotConfiguredProviders() throws Exception {
        mockMvc.perform(get("/api/workspaces/workspace-default/channels/channel-general/devops/summary")
                        .with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspaceId").value("workspace-default"))
                .andExpect(jsonPath("$.channelId").value("channel-general"))
                .andExpect(jsonPath("$.readOnly").value(true))
                .andExpect(jsonPath("$.paidFeaturesRequired").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.linkedProjects.length()").value(0))
                .andExpect(jsonPath("$.repositories.length()").value(0))
                .andExpect(jsonPath("$.openIssues.length()").value(0))
                .andExpect(jsonPath("$.mergeRequests.length()").value(0))
                .andExpect(jsonPath("$.pipelines.length()").value(0))
                .andExpect(jsonPath("$.releases.length()").value(0))
                .andExpect(jsonPath("$.providerReadiness[*].providerKey", hasItems("gitlab-ce-foss")))
                .andExpect(jsonPath("$.providerReadiness[*].readiness", hasItems("not_configured")))
                .andExpect(jsonPath("$.providerReadiness[*].unsupportedOperations[*]", hasItems("premium-ultimate-only-features")))
                .andExpect(content().string(not(containsString("CI_JOB_TOKEN"))))
                .andExpect(content().string(not(containsString("webhook_secret"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("aud", java.util.List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
