package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.service.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.service.ProductProfileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
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
        controllers = IdentityController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class, ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ProductProfileService.class})
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.local/realms/weave"
})
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ProductProfileOverrideRepository profileRepository;

    @Test
    void returnsAuthenticatedPrincipalDetails() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("email_verified", true)
                        .claim("locale", "en")
                        .claim("timezone", "Europe/Berlin")
                        .claim("azp", "weave-app")
                        .claim("aud", List.of("weave-app", "account"))
                        .claim("realm_access", Map.of("roles", List.of("member", "admin")))
                        .claim("groups", List.of("team-alpha", "team-beta")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.moduleSyncStatus.matrix").value("not_configured"))
                .andExpect(jsonPath("$.issuedFor").value("weave-app"))
                .andExpect(jsonPath("$.audience[0]").value("weave-app"))
                .andExpect(jsonPath("$.realmRoles[0]").value("admin"))
                .andExpect(jsonPath("$.groups[1]").value("team-beta"));
    }

    @Test
    void exposesCanonicalMeEndpoint() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user-123"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"));
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fallsBackToClientIdWhenAzpIsAbsent() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("client_id", "weave-app")
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedFor").value("weave-app"));
    }
}
