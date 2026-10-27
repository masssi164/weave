package com.massimotter.weave.backend.security.http;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

class SharedApiSecurityErrorsTest {
    @Test
    void sharedDenialsWorkWithoutAnyOptionalProtocol() throws Exception {
        var writer = new ApiErrorResponseWriter(new ObjectMapper());
        var request = new MockHttpServletRequest("GET", "/api/profile");
        request.addHeader("X-Request-Id", "request:test");
        var unauthorized = new MockHttpServletResponse();
        new ApiAuthenticationEntryPoint(writer, List.of()).commence(request, unauthorized,
                new AuthenticationCredentialsNotFoundException("private detail"));
        assertThat(unauthorized.getStatus()).isEqualTo(401);
        assertThat(unauthorized.getContentAsString()).contains("\"code\":\"unauthorized\"", "request:test")
                .doesNotContain("private detail", "errcode", "Matrix");
        var forbidden = new MockHttpServletResponse();
        new ApiAccessDeniedHandler(writer, List.of()).handle(request, forbidden,
                new AccessDeniedException("private detail"));
        assertThat(forbidden.getStatus()).isEqualTo(403);
        assertThat(forbidden.getContentAsString()).contains("\"code\":\"forbidden\"", "request:test")
                .doesNotContain("private detail", "errcode", "Matrix");
    }
}
