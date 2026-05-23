package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class ApiErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        write(request, response, status, defaultCode(status), message);
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message)
            throws IOException {
        write(request, response, status, code, message, Map.of());
    }

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code,
            String message, Map<String, Object> additionalDetails)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        String requestId = RequestIdFilter.requestId(request);
        response.setHeader(RequestIdFilter.HEADER, requestId);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("status", status.value());
        details.put("path", request.getRequestURI());
        details.put("error", status.getReasonPhrase());
        if (additionalDetails != null) {
            details.putAll(additionalDetails);
        }
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
                code,
                message,
                details,
                requestId));
    }

    private String defaultCode(HttpStatus status) {
        return status.name().toLowerCase().replace('_', '-');
    }

}
