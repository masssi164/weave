package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.OnboardingStatusProperties;
import com.massimotter.weave.backend.config.PlatformContractProperties;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.config.WeaveSecurityProperties;
import com.massimotter.weave.backend.config.WorkspaceCapabilityProperties;
import com.massimotter.weave.backend.service.OnboardingStatusService;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = OnboardingController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        OnboardingStatusService.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class
})
@EnableConfigurationProperties({
        PlatformContractProperties.class,
        WeaveSecurityProperties.class,
        WorkspaceCapabilityProperties.class,
        OnboardingStatusProperties.class,
        OAuth2ResourceServerProperties.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.weave.local/realms/weave",
        "weave.workspace.chat.dependency-url=https://matrix.weave.local",
        "weave.workspace.files.dependency-url=https://files.weave.local"
})
class OnboardingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void returnsFirstRunStatusForAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/onboarding/status").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("email_verified", true)
                        .claim("locale", "en")
                        .claim("timezone", "Europe/Berlin")
                        .claim("aud", List.of("weave-app"))
                        .claim("realm_access", Map.of("roles", List.of("member")))
                        .claim("groups", List.of("workspace-default")))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identity.userId").value("user-123"))
                .andExpect(jsonPath("$.identity.username").value("alice"))
                .andExpect(jsonPath("$.identity.groups[0]").value("workspace-default"))
                .andExpect(jsonPath("$.invite.status").value("active"))
                .andExpect(jsonPath("$.access.primaryRole").value("member"))
                .andExpect(jsonPath("$.profile.status").value("ready"))
                .andExpect(jsonPath("$.moduleProvisioning.identity.state").value("ready"))
                .andExpect(jsonPath("$.moduleProvisioning.profile.state").value("ready"))
                .andExpect(jsonPath("$.moduleProvisioning.matrix.state").value("ready"))
                .andExpect(jsonPath("$.moduleProvisioning.nextcloud.state").value("ready"))
                .andExpect(jsonPath("$.firstRunComplete").value(true))
                .andExpect(jsonPath("$.actions").isEmpty());
    }

    @ParameterizedTest
    @MethodSource("roleMappings")
    void mapsMvpRolesForFirstRunRouting(String realmRole, boolean canAdminister, boolean canInvite,
            boolean canUseModules) throws Exception {
        mockMvc.perform(get("/api/onboarding/status").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", realmRole + "-user")
                        .claim("name", "Role User")
                        .claim("email", realmRole + "@example.com")
                        .claim("email_verified", true)
                        .claim("aud", List.of("weave-app"))
                        .claim("realm_access", Map.of("roles", List.of(realmRole))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access.primaryRole").value(realmRole))
                .andExpect(jsonPath("$.access.canAdministerWorkspace").value(canAdminister))
                .andExpect(jsonPath("$.access.canInviteUsers").value(canInvite))
                .andExpect(jsonPath("$.access.canUseWorkspaceModules").value(canUseModules));
    }

    @Test
    void returnsPendingInviteAndProfileStatusWithoutRawDownstreamErrors() throws Exception {
        mockMvc.perform(get("/api/onboarding/status").with(jwt().jwt(jwt -> jwt
                        .subject("user-123")
                        .claim("preferred_username", "alice")
                        .claim("name", "Alice Example")
                        .claim("email", "alice@example.com")
                        .claim("email_verified", false)
                        .claim("weave_invite_status", "pending")
                        .claim("aud", List.of("weave-app"))
                        .claim("realm_access", Map.of("roles", List.of("member"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invite.status").value("pending"))
                .andExpect(jsonPath("$.profile.status").value("pending"))
                .andExpect(jsonPath("$.profile.missing[0]").value("email_verified"))
                .andExpect(jsonPath("$.moduleProvisioning.profile.state").value("pending"))
                .andExpect(jsonPath("$.firstRunComplete").value(false))
                .andExpect(jsonPath("$.actions[0]").isNotEmpty());
    }

    @Test
    void rejectsAnonymousRequests() throws Exception {
        mockMvc.perform(get("/api/onboarding/status"))
                .andExpect(status().isUnauthorized());
    }

    private static Stream<Arguments> roleMappings() {
        return Stream.of(
                Arguments.of("owner", true, true, true),
                Arguments.of("admin", true, true, true),
                Arguments.of("member", false, false, true),
                Arguments.of("guest", false, false, false));
    }
}
