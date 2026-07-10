package com.massimotter.weave.contract.mcp;

import java.util.List;
import java.util.Map;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolAnnotations;

public record MemberMcpToolDefinition(
        String name,
        String version,
        String domain,
        MemberMcpToolMode mode,
        String requiredCapability,
        boolean approvalRequired,
        boolean serverExecutable,
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

    public boolean argumentsMatchSchema(Map<String, Object> arguments) {
        Map<String, Object> safeArguments = arguments == null ? Map.of() : arguments;
        Object propertyNode = inputSchema.get("properties");
        if (!(propertyNode instanceof Map<?, ?> properties)
                || safeArguments.keySet().stream().anyMatch(key -> !properties.containsKey(key))) {
            return false;
        }
        Object requiredNode = inputSchema.get("required");
        if (requiredNode instanceof List<?> required) {
            for (Object field : required) {
                Object value = safeArguments.get(field);
                if (value == null || value instanceof String text && text.isBlank()) {
                    return false;
                }
            }
        }
        for (Map.Entry<String, Object> argument : safeArguments.entrySet()) {
            Object schemaNode = properties.get(argument.getKey());
            if (!(schemaNode instanceof Map<?, ?> propertySchema)
                    || !matchesType(argument.getValue(), propertySchema.get("type"))) {
                return false;
            }
            Object minimum = propertySchema.get("minimum");
            if (minimum instanceof Number requiredMinimum
                    && argument.getValue() instanceof Number supplied
                    && supplied.doubleValue() < requiredMinimum.doubleValue()) {
                return false;
            }
        }
        return true;
    }

    private boolean matchesType(Object value, Object type) {
        return switch (String.valueOf(type)) {
            case "string" -> value instanceof String;
            case "integer" -> value instanceof Byte
                    || value instanceof Short
                    || value instanceof Integer
                    || value instanceof Long;
            case "boolean" -> value instanceof Boolean;
            default -> false;
        };
    }

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
                false,
                definition.inputSchema(),
                definition.description());
    }
}
