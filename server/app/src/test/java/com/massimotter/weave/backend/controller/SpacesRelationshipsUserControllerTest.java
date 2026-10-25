package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.massimotter.weave.backend.config.*;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipResponse;
import com.massimotter.weave.backend.service.spaces.SpaceRelationshipUserService;
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

@WebMvcTest(controllers = SpacesRelationshipsUserController.class,
        excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
        ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
class SpacesRelationshipsUserControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean SpaceRelationshipUserService relationships;
    @MockitoBean JwtDecoder jwtDecoder;

    @Test
    void anonymousAndForeignOrganizationCannotReachRelations() throws Exception {
        mvc.perform(get("/api/spaces/workspace-default/relationships"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/spaces/workspace-default/relationships")
                        .with(jwt().jwt(token -> token.claim("organization",
                                Map.of("foreign", Map.of("id", "foreign"))))
                                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"))))
                .andExpect(status().isForbidden());
        verifyNoInteractions(relationships);
    }

    @Test
    void generatedUserTransportContainsOnlyStableMaterializedReferences() throws Exception {
        when(relationships.list(any(), eq("workspace-default"), isNull(), eq(25)))
                .thenReturn(new SpaceRelationshipListResponse(List.of(
                        new SpaceRelationshipResponse("relation:file:file:stable",
                                "CONTAINS", "FILE", "file:stable")), null));
        mvc.perform(get("/api/spaces/workspace-default/relationships").with(member()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.relationships[0].relationRef")
                        .value("relation:file:file:stable"))
                .andExpect(jsonPath("$.relationships[0].targetRef").value("file:stable"))
                .andExpect(jsonPath("$.relationships[0].providerRef").doesNotExist());
    }

    private org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor member() {
        return jwt().jwt(token -> token.subject("member")
                        .claim("organization", HumanJwtTestSupport.organizationWithRole("member")))
                .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"));
    }
}
