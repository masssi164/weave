package com.massimotter.weave.contract.mcp;

import java.util.Map;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolAnnotations;

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
        name = WeaveMcpTypes.text(name, "name");
        version = WeaveMcpTypes.text(version, "version");
        domain = WeaveMcpTypes.text(domain, "domain");
        requiredCapability = WeaveMcpTypes.text(requiredCapability, "requiredCapability");
        if (mode == null) throw new IllegalArgumentException("mode must not be null");
        inputSchema = WeaveMcpTypes.copyMap(inputSchema == null || inputSchema.isEmpty() ? Map.of("type", "object") : inputSchema);
        description = description == null || description.isBlank() ? name + " via Weave server policy boundary." : description.trim();
    }

    public boolean writeLike() { return mode.approvalRequiredByDefault(); }

    public WeaveMcpBridgeDtos.WeaveMcpToolDefinition asBridgeDefinition() {
        return new WeaveMcpBridgeDtos.WeaveMcpToolDefinition(
                name,
                version,
                domain,
                mode.toBridgeMode(),
                requiredCapability,
                approvalRequired,
                inputSchema,
                new WeaveMcpToolAnnotations(mode == MemberMcpToolMode.READ, false, false),
                description);
    }

    public static MemberMcpToolDefinition fromBridgeDefinition(WeaveMcpBridgeDtos.WeaveMcpToolDefinition definition) {
        return new MemberMcpToolDefinition(
                definition.name(),
                definition.version(),
                definition.domain(),
                MemberMcpToolMode.fromBridgeMode(definition.mode()),
                definition.requiredCapability(),
                definition.approvalRequired(),
                definition.inputSchema(),
                definition.description());
    }
}
