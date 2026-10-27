package com.massimotter.weave.backend.security.device;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;
import jakarta.servlet.FilterChain;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DeviceCredentialHttpScopeTest {
    @Test
    void omittedScopeAndSiblingPathCannotAuthenticateADevice() throws Exception {
        var service = mock(DeviceCredentialService.class);
        var writer = new ApiErrorResponseWriter(new ObjectMapper());
        var chain = mock(FilterChain.class);
        var request = new MockHttpServletRequest("GET", "/dav/files");
        request.addHeader("Authorization", "Basic dXNlcjpzZWNyZXQ=");
        var response = new MockHttpServletResponse();
        new DeviceCredentialAuthenticationFilter(service, writer, List.of()).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        request = new MockHttpServletRequest("GET", "/dav/files-other");
        request.addHeader("Authorization", "Basic dXNlcjpzZWNyZXQ=");
        new DeviceCredentialAuthenticationFilter(service, writer,
                List.of(new DeviceCredentialHttpScope("files", "/dav/files"))).doFilter(request, response, chain);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(service);
    }

    @Test
    void duplicatePathOwnershipIsRejected() {
        var scope = new DeviceCredentialHttpScope("example", "/dav/example");
        assertThatThrownBy(() -> new DeviceCredentialAuthenticationFilter(mock(DeviceCredentialService.class),
                new ApiErrorResponseWriter(new ObjectMapper()), List.of(scope, scope)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("duplicated");
        assertThat(scope.matches("/dav/example")).isTrue();
        assertThat(scope.matches("/dav/example/item")).isTrue();
        assertThat(scope.matches("/dav/example-other/item")).isFalse();
    }
}
