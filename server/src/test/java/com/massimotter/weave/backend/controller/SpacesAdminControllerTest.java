package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionResponse;
import com.massimotter.weave.backend.service.spaces.SpaceAdminApiService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = SpacesAdminController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
class SpacesAdminControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SpaceAdminApiService spaces;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void anonymousForeignOrganizationAndMalformedTransportNeverReachSetup() throws Exception {
        mvc.perform(post("/api/admin/spaces").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceRef\":\"workspace-default\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/spaces").with(jwt().jwt(token -> token
                        .claim("organization", Map.of("foreign", Map.of("id", "foreign"))))
                        .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceRef\":\"workspace-default\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/spaces").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceRef\":\"bad/ref\",\"memberAccountRefs\":[]}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(spaces);
    }

    @Test
    void ownerUsesGeneratedJsonRequestAndReceivesStableResult() throws Exception {
        when(spaces.provision(any(), any())).thenReturn(
                new SpaceProvisionResponse("workspace-default", "CREATED"));
        mvc.perform(post("/api/admin/spaces").with(owner())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"spaceRef\":\"workspace-default\",\"memberAccountRefs\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spaceRef").value("workspace-default"))
                .andExpect(jsonPath("$.result").value("CREATED"));
        verify(spaces).provision(any(), any());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor owner() {
        return jwt().jwt(token -> token.subject("owner")
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("owner")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
