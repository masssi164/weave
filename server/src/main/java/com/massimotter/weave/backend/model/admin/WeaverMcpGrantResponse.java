package com.massimotter.weave.backend.model.admin;

import java.util.List;

public record WeaverMcpGrantResponse(
        String serverKey,
        List<String> tools,
        boolean approvalRequired) {
    public WeaverMcpGrantResponse {
        serverKey = serverKey == null || serverKey.isBlank() ? "unnamed-mcp-server" : serverKey.trim();
        tools = tools == null ? List.of() : tools.stream()
                .filter(tool -> tool != null && !tool.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
