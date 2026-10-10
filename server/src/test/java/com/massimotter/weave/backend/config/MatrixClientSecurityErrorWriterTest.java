package com.massimotter.weave.backend.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;

class MatrixClientSecurityErrorWriterTest {
  @Test
  void filterDenialKeepsTheMatrixClientServerErrorContract() throws Exception {
    MockHttpServletRequest request = matrixRequest();
    MockHttpServletResponse missing = new MockHttpServletResponse();
    new ApiAuthenticationEntryPoint(null).commence(
        request, missing, new AuthenticationCredentialsNotFoundException("missing"));
    assertThat(missing.getStatus()).isEqualTo(401);
    assertThat(missing.getContentAsString()).contains("\"errcode\":\"M_MISSING_TOKEN\"");
    assertThat(missing.getContentAsString()).contains("\"error\":");

    request.addHeader("Authorization", "Bearer invalid");
    MockHttpServletResponse invalid = new MockHttpServletResponse();
    new ApiAuthenticationEntryPoint(null).commence(
        request, invalid, new AuthenticationCredentialsNotFoundException("invalid"));
    assertThat(invalid.getStatus()).isEqualTo(401);
    assertThat(invalid.getContentAsString()).contains("\"errcode\":\"M_UNKNOWN_TOKEN\"");

    MockHttpServletResponse foreign = new MockHttpServletResponse();
    new ApiAccessDeniedHandler(null).handle(
        request, foreign, new AccessDeniedException("foreign organization"));
    assertThat(foreign.getStatus()).isEqualTo(403);
    assertThat(foreign.getContentType()).contains("application/json");
    assertThat(foreign.getContentAsString()).contains("\"errcode\":\"M_FORBIDDEN\"");
    assertThat(foreign.getHeader("X-Weave-Projection")).isEqualTo("matrix-client-server");
  }

  private static MockHttpServletRequest matrixRequest() {
    return new MockHttpServletRequest("GET", "/_matrix/client/v3/account/whoami");
  }
}
