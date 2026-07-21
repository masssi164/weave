package com.massimotter.weave.mcp;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import tools.jackson.databind.json.JsonMapper;

final class McpBearerChallengeWriter {
    private final McpWorkloadProperties properties;
    private final JsonMapper mapper;

    McpBearerChallengeWriter(McpWorkloadProperties properties, JsonMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
    }

    void unauthorized(HttpServletResponse response) throws IOException {
        challenge(response, 401, "invalid_token", null,
                "A valid per-cell Weaver workload token is required.");
    }

    void insufficientScope(HttpServletResponse response) throws IOException {
        challenge(response, 403, "insufficient_scope", String.join(" ", properties.requiredScopes()),
                "The workload token does not carry the exact MCP scope set.");
    }

    void forbidden(HttpServletResponse response) throws IOException {
        response(response, 403, "access_denied", "The workload has no current MCP authorization.");
    }

    void badRequest(HttpServletResponse response) throws IOException {
        response(response, 400, "invalid_request", "The MCP request does not satisfy the negotiated extension contract.");
    }

    void unavailable(HttpServletResponse response) throws IOException {
        response(response, 503, "temporarily_unavailable", "The MCP authorization authority is unavailable.");
    }

    private void challenge(
            HttpServletResponse response,
            int status,
            String error,
            String scope,
            String description) throws IOException {
        StringBuilder value = new StringBuilder("Bearer resource_metadata=\"")
                .append(properties.resourceMetadataUri())
                .append("\", error=\"")
                .append(error)
                .append("\"");
        if (scope != null) {
            value.append(", scope=\"").append(scope).append("\"");
        }
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, value.toString());
        response(response, status, error, description);
    }

    private void response(HttpServletResponse response, int status, String error, String description)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsetsHolder.UTF_8);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        response.setHeader("X-Content-Type-Options", "nosniff");
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("error_description", description);
        mapper.writeValue(response.getOutputStream(), body);
    }

    private static final class StandardCharsetsHolder {
        private static final String UTF_8 = java.nio.charset.StandardCharsets.UTF_8.name();
    }
}
