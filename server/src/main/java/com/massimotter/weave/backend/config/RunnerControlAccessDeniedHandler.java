package com.massimotter.weave.backend.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Support-safe authorization failure for an authenticated Runner workload. */
public final class RunnerControlAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public RunnerControlAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = Objects.requireNonNull(errorResponseWriter, "errorResponseWriter");
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException failure)
            throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "runner-control-forbidden",
                "The authenticated Runner is not authorized for this control operation.");
    }
}
