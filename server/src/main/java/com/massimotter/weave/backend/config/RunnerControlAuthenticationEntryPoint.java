package com.massimotter.weave.backend.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/** Support-safe authentication failure for the certificate-only Runner control plane. */
public final class RunnerControlAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter errorResponseWriter;

    public RunnerControlAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = Objects.requireNonNull(errorResponseWriter, "errorResponseWriter");
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException failure)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "runner-certificate-required",
                "A valid active Runner client certificate is required.");
    }
}
