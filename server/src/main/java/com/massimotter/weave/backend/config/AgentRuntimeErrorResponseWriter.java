package com.massimotter.weave.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.model.agentruntime.AgentRuntimeErrorEnvelope;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Serializes only the closed ARC error envelope; provider failure text never crosses this edge. */
@Component
public final class AgentRuntimeErrorResponseWriter {
    private final ObjectMapper objectMapper;

    public AgentRuntimeErrorResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String capabilityState,
            boolean retryable,
            String userMessage) throws IOException {
        write(request, response, status, code, capabilityState, retryable, userMessage, Map.of());
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            String code,
            String capabilityState,
            boolean retryable,
            String userMessage,
            Map<String, Object> supportDetail) throws IOException {
        String requestId = RequestIdFilter.requestId(request);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader(RequestIdFilter.HEADER, requestId);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        objectMapper.writeValue(response.getWriter(), new AgentRuntimeErrorEnvelope(
                code, capabilityState, retryable, userMessage, auditRef(requestId), supportDetail));
    }

    public String auditRef(HttpServletRequest request) {
        return auditRef(RequestIdFilter.requestId(request));
    }

    private static String auditRef(String requestId) {
        return "audit:arc:" + RuntimeWorkloadOwnership.fingerprint(requestId).substring(7);
    }
}
