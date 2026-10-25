package com.massimotter.weave.backend.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.massimotter.weave.backend.security.http.ApiAccessDeniedHandler;
import com.massimotter.weave.backend.security.http.ApiAuthenticationEntryPoint;
import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import com.massimotter.weave.backend.config.SecurityConfig;
import com.massimotter.weave.backend.exception.ApiExceptionHandler;
import com.massimotter.weave.backend.model.identity.OrganizationMemberPageResponse;
import com.massimotter.weave.backend.model.identity.OrganizationMemberResponse;
import com.massimotter.weave.backend.service.OrganizationMemberAdministrationService;
import com.massimotter.weave.backend.support.HumanJwtTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = OrganizationMemberAdministrationController.class,
    excludeAutoConfiguration = OAuth2ResourceServerAutoConfiguration.class)
@Import({SecurityConfig.class, ApiAuthenticationEntryPoint.class,
    ApiAccessDeniedHandler.class, ApiErrorResponseWriter.class, ApiExceptionHandler.class})
class OrganizationMemberAdministrationControllerTest {
  private static final String PATH = "/api/admin/organizations/acme/members";
  private static final String ACCOUNT = "acct_0123456789abcdef0123456789abcdef";

  @Autowired MockMvc mvc;
  @MockitoBean OrganizationMemberAdministrationService service;
  @MockitoBean JwtDecoder jwtDecoder;

  @Test
  void anonymousAndNonAdministratorRequestsNeverReachMemberProjection() throws Exception {
    for (String path : List.of(PATH, PATH + "/mem_target")) {
      mvc.perform(get(path)).andExpect(status().isUnauthorized());
      for (String role : List.of("member", "guest")) {
        mvc.perform(get(path).with(jwt().jwt(token -> token.subject("requester")
                .claim("organization", HumanJwtTestSupport.organizationWithRole(role)))
            .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT)))))
            .andExpect(status().isForbidden());
      }
    }
    verifyNoInteractions(service);
  }

  @Test
  void authorizedGetAndListIncludeCanonicalReferenceWithoutChangingRoleOrEnabledState()
      throws Exception {
    var response = new OrganizationMemberResponse("mem_target", ACCOUNT,
        "member@example.test", "Member", "member", List.of(), false, "v1");
    when(service.get(eq("acme"), eq("mem_target"), any())).thenReturn(response);
    when(service.list(eq("acme"), isNull(), anyInt(), any()))
        .thenReturn(new OrganizationMemberPageResponse(List.of(response), null));
    for (String role : List.of("owner", "admin")) {
      var administrator = jwt().jwt(token -> token.subject("requester")
              .claim("organization", HumanJwtTestSupport.organizationWithRole(role)))
          .authorities(new SimpleGrantedAuthority("SCOPE_weave:workspace"),
                new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(java.util.Locale.ROOT)));
      mvc.perform(get(PATH + "/mem_target").with(administrator))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.accountRef").value(ACCOUNT))
          .andExpect(jsonPath("$.memberHandle").value("mem_target"))
          .andExpect(jsonPath("$.enabled").value(false))
          .andExpect(jsonPath("$.role").value("member"))
          .andExpect(jsonPath("$.subject").doesNotExist())
          .andExpect(jsonPath("$.accessToken").doesNotExist());
      mvc.perform(get(PATH).with(administrator))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.items[0].accountRef").value(ACCOUNT))
          .andExpect(jsonPath("$.items[0].enabled").value(false));
    }
  }
}
