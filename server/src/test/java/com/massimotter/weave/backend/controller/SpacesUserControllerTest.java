package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.spaces.SpaceUserListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceUserResponse;
import com.massimotter.weave.backend.service.spaces.SpaceUserApiService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SpacesUserController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
class SpacesUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SpaceUserApiService spaces;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void anonymousAndForeignOrganizationCannotReachSpaceService() throws Exception {
        mvc.perform(get("/api/spaces")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/spaces").with(jwt().jwt(token -> token
                        .claim("organization", Map.of("foreign", Map.of("id", "foreign"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(spaces);
    }

    @Test
    void memberGetsOnlySpaceReferencesFromTheUserTransport() throws Exception {
        when(spaces.list(any(), isNull(), eq(25))).thenReturn(new SpaceUserListResponse(
                List.of(new SpaceUserResponse("space:team")), null));
        when(spaces.inspect(any(), eq("space:team")))
                .thenReturn(new SpaceUserResponse("space:team"));
        mvc.perform(get("/api/spaces").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaces[0].spaceRef").value("space:team"))
                .andExpect(jsonPath("$.spaces[0].providerRef").doesNotExist());
        mvc.perform(get("/api/spaces/space:team").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceRef").value("space:team"));
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor member() {
        return jwt().jwt(token -> token.subject("member")
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
