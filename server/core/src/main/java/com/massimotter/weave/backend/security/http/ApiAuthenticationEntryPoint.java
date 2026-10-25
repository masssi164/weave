package com.massimotter.weave.backend.security.http;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ApiErrorResponseWriter errorResponseWriter;
    private final java.util.List<ProtocolSecurityErrorHandler> protocolHandlers;

    public ApiAuthenticationEntryPoint(ApiErrorResponseWriter errorResponseWriter,
            java.util.List<ProtocolSecurityErrorHandler> protocolHandlers) {
        this.errorResponseWriter = errorResponseWriter;
        this.protocolHandlers = java.util.List.copyOf(protocolHandlers);
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException, ServletException {
        for (var handler : protocolHandlers) {
            if (handler.writeAuthenticationError(request, response)) return;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                "unauthorized",
                "Bearer authentication is required and must satisfy the first-party Weave token contract.");
    }
}
