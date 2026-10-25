package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe admin binding for an approved MCP server projected to Weaver runtimes.")
public record McpServerBindingResponse(
        String serverKey,
        String displayName,
        String transport,
        String endpointRef,
        String authRef,
        List<String> allowedTools,
        List<String> allowedCapabilities,
        boolean approvalRequiredForWrites,
        boolean enabled,
        String readinessState,
        boolean supportSafe,
        boolean rawEndpointExposed,
        boolean rawServerConfigExposed,
        boolean secretValuesExposed,
        List<String> auditRefs,
        List<String> nextActions) {
    public McpServerBindingResponse {
        serverKey = serverKey == null || serverKey.isBlank() ? "unnamed-mcp-server" : serverKey.trim();
        displayName = displayName == null || displayName.isBlank() ? serverKey : displayName.trim();
        transport = transport == null || transport.isBlank() ? "streamable-http" : transport.trim();
        endpointRef = endpointRef == null || endpointRef.isBlank() ? "internal://weave-mcp/streamable-http" : endpointRef.trim();
        authRef = authRef == null || authRef.isBlank() ? "credentialref://weave/mcp/" + serverKey + "/runtime-token" : authRef.trim();
        allowedTools = allowedTools == null ? List.of() : allowedTools.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        allowedCapabilities = allowedCapabilities == null ? List.of() : allowedCapabilities.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }
}
