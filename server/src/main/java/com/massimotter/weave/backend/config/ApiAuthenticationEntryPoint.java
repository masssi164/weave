package com.massimotter.weave.backend.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        if (isFilesWebDav(request)) {
            errorResponseWriter.writeFilesWebDav(
                    request,
                    response,
                    HttpStatus.UNAUTHORIZED,
                    "unauthorized",
                    "Authentication is required for the Weave Files WebDAV facade.");
            return;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Bearer authentication is required and must satisfy the first-party Weave token contract.");
    }

    private boolean isFilesWebDav(HttpServletRequest request) {
        String path = request.getRequestURI();
        return "/dav/files".equals(path) || path.startsWith("/dav/files/");
    }
}
