package com.massimotter.weave.backend.security.http;

import com.massimotter.weave.backend.api.error.ApiErrorResponseWriter;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorResponseWriter;
    private final java.util.List<ProtocolSecurityErrorHandler> protocolHandlers;

    public ApiAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter,
            java.util.List<ProtocolSecurityErrorHandler> protocolHandlers) {
        this.errorResponseWriter = errorResponseWriter;
        this.protocolHandlers = java.util.List.copyOf(protocolHandlers);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        for (var handler : protocolHandlers) {
            if (handler.writeAccessDenied(request, response)) return;
        }
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "forbidden",
                "The bearer token is authenticated but missing the required weave:workspace scope or selected-organization role.");
    }
}
