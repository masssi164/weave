package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.DevopsProviderConfiguration;
import com.massimotter.weave.backend.config.ProviderCoreConfiguration;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.office.port.DisabledOfficeProvider;
import com.massimotter.weave.backend.provider.ProviderRegistry;
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

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void providerStatusRequiresWorkspaceScope() throws Exception {
        mockMvc.perform(get("/api/providers/status"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("unauthorized"));
    }

    @Test
    void providerStatusReportsAllFacadeSeamsWithoutSecrets() throws Exception {
        mockMvc.perform(get("/api/providers/status").with(workspaceJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.backendOwnedFacades").value(true))
                .andExpect(jsonPath("$.flutterDirectProviderCallsAllowed").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providers[*].module", hasItems(
                        "identity-realm", "files", "office", "calendar", "contacts", "forms", "boards",
                        "meetings", "source-control", "ci", "issue-tracker", "release")))
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("aud", java.util.List.of("weave-app")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
