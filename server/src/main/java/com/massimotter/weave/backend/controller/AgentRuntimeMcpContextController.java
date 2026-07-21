package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.agentruntime.application.McpWorkloadAuthorizationService;
import com.massimotter.weave.backend.agentruntime.port.McpWorkloadAuthorizationException;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.agentruntime.McpWorkloadContextResponse;
import io.swagger.v3.oas.annotations.Hidden;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Hidden
@RestController
@RequestMapping("/api/internal/agent-runtime")
@ConditionalOnProperty(name = "weave.agent-runtime.workload-identity.enabled", havingValue = "true")
public class AgentRuntimeMcpContextController {
    private final McpWorkloadAuthorizationService authorization;

    public AgentRuntimeMcpContextController(McpWorkloadAuthorizationService authorization) {
        this.authorization = authorization;
    }

    @PostMapping("/mcp-context")
    public ResponseEntity<McpWorkloadContextResponse> resolve(JwtAuthenticationToken authentication) {
        try {
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noStore())
                    .header(HttpHeaders.PRAGMA, "no-cache")
                    .header("X-Content-Type-Options", "nosniff")
                    .body(McpWorkloadContextResponse.from(authorization.authorize(authentication.getToken())));
        } catch (McpWorkloadAuthorizationException failure) {
            throw error(failure.authorityUnavailable());
        }
    }

    private static ApiErrorException error(boolean unavailable) {
        return new ApiErrorException(
                unavailable ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.FORBIDDEN,
                unavailable ? "mcp-workload-authority-unavailable" : "mcp-workload-forbidden",
                unavailable
                        ? "The MCP workload authority is temporarily unavailable."
                        : "The exchanged workload token has no current authorized cell context.",
                Map.of());
    }
}
