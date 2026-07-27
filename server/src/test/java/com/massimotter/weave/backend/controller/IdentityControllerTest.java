package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.service.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.service.ProductProfileService;
import com.massimotter.weave.backend.service.OrganizationIdentityContextResolver;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = IdentityController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        OrganizationIdentityContextResolver.class,
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ProductProfileService.class
})
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave"
})
class IdentityControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private ProductProfileOverrideRepository profileRepository;

    @Test
    void returnsAuthenticatedPrincipalDetails() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("weave_tenant_id", "acme-prod")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("email_verified", true)
                        .claim("locale", "en")
                        .claim("timezone", "Europe/Berlin")
                        .claim("azp", "weave-app")
                        .claim("aud", List.of("weave-app", "account"))
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member", "admin"))))
                        .claim("groups", List.of("team-alpha", "team-beta"))
                        .claim("weave_context_roles", List.of("channel-admin")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", startsWith("acct_")))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.moduleSyncStatus.matrix").value("not_configured"))
                .andExpect(jsonPath("$.issuedFor").value("weave-app"))
                .andExpect(jsonPath("$.audience[0]").value("weave-app"))
                .andExpect(jsonPath("$.organizationId").value("acme-prod"))
                .andExpect(jsonPath("$.subject").value("user-123"))
                .andExpect(jsonPath("$.primaryIdentityKey").value("issuer+subject:https://auth.example.invalid/realms/acme#user-123"))
                .andExpect(jsonPath("$.emailPrimaryKey").value(false))
                .andExpect(jsonPath("$.providerRoleMappings[0]").value("group_claim:team-alpha"))
                .andExpect(jsonPath("$.contextRoles[0]").value("channel-admin"))
                .andExpect(jsonPath("$.groups[1]").value("team-beta"));
    }

    @Test
    void exposesCanonicalMeEndpoint() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", startsWith("acct_")))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"));
    }

    @Test
    void ignoresOperatorAsAMemberProductRole() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("operator-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("preferred_username", "ops")
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("operator"))))
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles").isEmpty())
                .andExpect(jsonPath("$.providerRoleMappings").isEmpty());
    }

    @Test
    void emailRenameDoesNotChangePrimaryIdentity() throws Exception {
        mockMvc.perform(get("/api/me").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("email", "alice.renamed@example.com")
                        .claim("preferred_username", "alice")
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.primaryIdentityKey").value("issuer+subject:https://auth.example.invalid/realms/acme#user-123"))
                .andExpect(jsonPath("$.userId", startsWith("acct_")))
                .andExpect(jsonPath("$.userId", not("alice.renamed@example.com")))
                .andExpect(jsonPath("$.emailPrimaryKey").value(false));
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
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("client_id", "weave-app")
                        .claim("aud", List.of("weave-app")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuedFor").value("weave-app"));
    }
}
