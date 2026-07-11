package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.service.ProductProfileOverride;
import com.massimotter.weave.backend.service.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.service.ProductProfileService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = {ProfileController.class, IdentityController.class},
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class,
        ProductProfileService.class
})
@org.springframework.test.context.TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.test/realms/weave"
})
class ProfileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ProductProfileOverrideRepository profileRepository;

    private final Map<String, ProductProfileOverride> profileOverrides = new HashMap<>();

    @BeforeEach
    void setUpProfileRepository() {
        profileOverrides.clear();
        when(profileRepository.findByPrimaryIdentityKey(anyString())).thenAnswer(invocation -> profileOverrides.get(invocation.getArgument(0)));
        when(profileRepository.saveForPrimaryIdentityKey(anyString(), any(ProductProfileOverride.class))).thenAnswer(invocation -> {
            profileOverrides.put(invocation.getArgument(0), invocation.getArgument(1));
            return invocation.getArgument(1);
        });
    }

    @Test
    void returnsProfileDerivedFromAuthenticatedPrincipal() throws Exception {
        mockMvc.perform(get("/api/profile").with(profileJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", startsWith("acct_")))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.displayName").value("Alice Example"))
                .andExpect(jsonPath("$.avatar").value("https://example.test/alice.png"))
                .andExpect(jsonPath("$.locale").value("en"))
                .andExpect(jsonPath("$.timezone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.profileVisibility").value("workspace"))
                .andExpect(jsonPath("$.moduleSyncStatus.matrix").value("not_configured"));
    }

    @Test
    void patchesMutableProfileFieldsAndReflectsThemInMeSnapshot() throws Exception {
        String body = """
                {
                  "displayName": "Alice Weave",
                  "locale": "de-DE",
                  "timezone": "Europe/Vienna",
                  "accessibilityPreferences": {
                    "reducedMotion": "true",
                    "density": "comfortable"
                  },
                  "profileVisibility": "private"
                }
                """;

        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .with(profileJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Weave"))
                .andExpect(jsonPath("$.locale").value("de-DE"))
                .andExpect(jsonPath("$.timezone").value("Europe/Vienna"))
                .andExpect(jsonPath("$.accessibilityPreferences.reducedMotion").value("true"))
                .andExpect(jsonPath("$.profileVisibility").value("private"))
                .andExpect(jsonPath("$.moduleSyncStatus.nextcloud").value("not_configured"));

        mockMvc.perform(get("/api/me").with(profileJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Alice Weave"))
                .andExpect(jsonPath("$.locale").value("de-DE"))
                .andExpect(jsonPath("$.timezone").value("Europe/Vienna"));
    }

    @Test
    void exposesProfileSyncStatusEndpoint() throws Exception {
        mockMvc.perform(get("/api/profile/sync-status").with(profileJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matrix").value("not_configured"))
                .andExpect(jsonPath("$.nextcloud").value("not_configured"));
    }

    @Test
    void exposesSupportSafeProfileReadinessContract() throws Exception {
        mockMvc.perform(get("/api/profile/readiness").with(profileJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contractId").value("CEFACADE"))
                .andExpect(jsonPath("$.endpoint").value("/profile/readiness"))
                .andExpect(jsonPath("$.backendOwnedFacade").value(true))
                .andExpect(jsonPath("$.directProviderCallsAllowed").value(false))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.readiness").value("ready"))
                .andExpect(jsonPath("$.unsupportedOperations", hasItem("direct-frontend-keycloak-admin")));
    }

    @Test
    void rejectsInvalidTimezoneWithNormalizedError() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"timezone\":\"Mars/Olympus\"}")
                        .with(profileJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("validation-error"))
                .andExpect(jsonPath("$.details.fields.timezone").value("must be a valid IANA timezone"));
    }

    @Test
    void rejectsAnonymousProfileUpdates() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice Weave\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsProfileUpdatesWithoutWorkspaceScope() throws Exception {
        mockMvc.perform(patch("/api/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Alice Weave\"}")
                        .with(jwt().jwt(jwt -> jwt
                                .subject("user-123")
                                .claim("iss", "https://auth.example.invalid/realms/acme")
                                .claim("preferred_username", "alice")
                                .claim("aud", List.of("weave-app")))))
                .andExpect(status().isForbidden());
    }

    private static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor profileJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("email_verified", true)
                        .claim("locale", "en")
                        .claim("timezone", "Europe/Berlin")
                        .claim("picture", "https://example.test/alice.png")
                        .claim("azp", "weave-app")
                        .claim("aud", List.of("weave-app", "account"))
                        .claim("resource_access", Map.of("weave-app", Map.of("roles", List.of("member"))))
                        .claim("groups", List.of("team-alpha")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
