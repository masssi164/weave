package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.config.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.config.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(
        controllers = MatrixClientServerProjectionController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({
        SecurityConfig.class,
        ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class,
        ApiErrorResponseWriter.class,
        ApiExceptionHandler.class
})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://auth.example.invalid/realms/weave"
})
class MatrixClientServerProjectionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtDecoder jwtDecoder;

    @Test
    void matrixClientServerProjectionRequiresWorkspaceToken() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/sync"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void matrixClientServerProjectionAdvertisesReservedMethods() throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf("OPTIONS"), "/_matrix/client/v3/sync")
                        .with(workspaceJwt()))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.ALLOW, "OPTIONS, GET, POST, PUT"))
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"));
    }

    @Test
    void matrixClientServerProjectionFailsClosedWithoutProviderPayloads() throws Exception {
        mockMvc.perform(get("/_matrix/client/v3/sync")
                        .with(workspaceJwt()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("X-Weave-Projection", "matrix-client-server"))
                .andExpect(jsonPath("$.errcode").value("M_WEAVE_MATRIX_PROJECTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.weaveBoundary").value("northbound-matrix-client-server"))
                .andExpect(jsonPath("$.canonicalDomain").value("chat"))
                .andExpect(jsonPath("$.supportSafe").value(true))
                .andExpect(jsonPath("$.providerDataPlaneExposed").value(false))
                .andExpect(content().string(not(containsString("BridgeAdapter"))))
                .andExpect(content().string(not(containsString("providerAccessToken"))))
                .andExpect(content().string(not(containsString("access_token"))))
                .andExpect(content().string(not(containsString("homeserver"))));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor workspaceJwt() {
        return jwt().jwt(jwt -> jwt
                        .subject("user@example.com")
                        .claim("iss", "https://auth.example.invalid/realms/acme")
                        .claim("aud", java.util.List.of("weave-app"))
                        .claim("weave_tenant_id", "tenant-default")
                        .claim("realm_access", java.util.Map.of("roles", java.util.List.of("member"))))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
