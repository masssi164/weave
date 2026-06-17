package com.massimotter.weave.contract.mcp;

import java.util.Map;

public record MemberMcpToolDefinition(
        String name,
        String version,
        String domain,
        MemberMcpToolMode mode,
        String requiredCapability,
        boolean approvalRequired,
        Map<String, Object> inputSchema,
        String description) {

    public MemberMcpToolDefinition {
        name = text(name, "name");
        version = text(version, "version");
        domain = text(domain, "domain");
        requiredCapability = text(requiredCapability, "requiredCapability");
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        inputSchema = Map.copyOf(inputSchema == null ? Map.of("type", "object") : inputSchema);
        description = description == null || description.isBlank() ? name + " via Weave server policy boundary." : description.trim();
    }

    public boolean writeLike() { return mode.approvalRequiredByDefault(); }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
