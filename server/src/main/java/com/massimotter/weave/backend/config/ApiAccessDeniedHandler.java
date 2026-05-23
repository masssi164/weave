package com.massimotter.weave.backend.config;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ApiErrorResponseWriter errorResponseWriter;

    public ApiAccessDeniedHandler(ApiErrorResponseWriter errorResponseWriter) {
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {
        errorResponseWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                "forbidden",
                "The bearer token is authenticated but missing the required weave:workspace scope.");
    }
}
