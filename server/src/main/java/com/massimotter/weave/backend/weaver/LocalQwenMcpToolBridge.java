package com.massimotter.weave.backend.weaver;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LocalQwenMcpToolBridge {

    public static final String MCP_SERVER_ID = "mcp-weave-domain-tools";
    public static final String RUNTIME_PROFILE_VERSION = "weaver-runtime-profile:v1";

    private final WeaverToolRegistry toolRegistry;

    public LocalQwenMcpToolBridge(WeaverToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public List<WeaverDomainToolDefinition> offeredTools(List<String> grantedCapabilities) {
        return toolRegistry.discover(grantedCapabilities).stream()
                .filter(definition -> !definition.writeLike())
                .toList();
    }

    public QwenMcpToolTurn execute(QwenMcpToolRequest request) {
        List<WeaverDomainToolDefinition> offeredTools = offeredTools(request.grantedCapabilities());
        WeaverDomainToolDefinition offeredTool = offeredTools.stream()
                .filter(definition -> definition.name().equals(request.toolName()))
                .findFirst()
                .orElse(null);
        if (offeredTool == null) {
            return denied(request, "tool_not_offered", "Local Qwen requested a tool that is not in the governed read-only MCP offer.");
        }
        if (!inputAllowedBySchema(offeredTool, request.input()) || containsProviderShapedArgument(request.input())) {
            return denied(request, "overbroad_args", "Local Qwen requested unknown, overbroad, or provider-shaped arguments; invocation failed closed.");
        }
        WeaverToolInvocationResult result = toolRegistry.invoke(new WeaverToolInvocationRequest(
                request.toolName(),
                request.userRef(),
                request.runtimeProfileHash(),
                request.runtimeProfileUserRef(),
                request.runtimeProfileSignature(),
                request.runtimeProfileRevoked(),
                request.runtimeTokenExpiresAt(),
                request.consentGranted(),
                request.grantedCapabilities(),
                request.scopedToolGrants(),
                request.input(),
                null));
        Map<String, Object> evidence = baseEvidence(request, result.status());
        evidence.put("toolCallReceived", true);
        evidence.put("toolOffered", true);
        evidence.put("toolResultFedBackToModel", "ok".equals(result.status()));
        evidence.put("auditRef", result.redactedResult().getOrDefault("auditRef", "audit://weaver-tool/" + request.toolName() + "/" + result.status()));
        evidence.put("approvalState", result.approvalRequired() ? "required" : "not_required");
        evidence.put("denyState", "ok".equals(result.status()) ? "allowed" : result.status());
        evidence.put("rawProviderPayloadIncluded", false);
        evidence.put("visibleThinkingTreatedAsAuthority", false);
        return new QwenMcpToolTurn("ok".equals(result.status()), result.status(), result.supportSafeMessage(), Map.copyOf(evidence), result.redactedResult());
    }

    private QwenMcpToolTurn denied(QwenMcpToolRequest request, String status, String message) {
        Map<String, Object> evidence = baseEvidence(request, status);
        evidence.put("toolCallReceived", true);
        evidence.put("toolResultFedBackToModel", false);
        evidence.put("approvalState", "denied");
        evidence.put("denyState", status);
        evidence.put("rawProviderPayloadIncluded", false);
        evidence.put("visibleThinkingTreatedAsAuthority", false);
        return new QwenMcpToolTurn(false, status, message, Map.copyOf(evidence), Map.of("auditRef", evidence.get("auditRef")));
    }

    private Map<String, Object> baseEvidence(QwenMcpToolRequest request, String decision) {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("channelId", request.channelId());
        evidence.put("modelRef", request.modelRef());
        evidence.put("runtimeProfileHash", request.runtimeProfileHash());
        evidence.put("runtimeProfileVersion", request.runtimeProfileVersion());
        evidence.put("mcpServerId", MCP_SERVER_ID);
        evidence.put("toolId", request.toolName());
        evidence.put("tool", request.toolName());
        evidence.put("domain", domainOf(request.toolName()));
        evidence.put("providerRef", "provider:domain-facade");
        evidence.put("credentialRef", "credentialref://weave/runtime/short-lived");
        evidence.put("decision", decision);
        evidence.put("auditRef", "audit://weaver-tool/" + (request.toolName() == null ? "unknown" : request.toolName()) + "/" + decision);
        evidence.put("supportSafe", true);
        return evidence;
    }

    private static String domainOf(String toolName) {
        if (toolName == null || !toolName.contains(".")) {
            return "weaver-runtime";
        }
        return toolName.substring(0, toolName.indexOf('.'));
    }

    private static boolean inputAllowedBySchema(WeaverDomainToolDefinition definition, Map<String, Object> input) {
        return valueAllowedBySchema(definition.inputSchema(), input);
    }

    @SuppressWarnings("unchecked")
    private static boolean valueAllowedBySchema(Map<String, Object> schema, Object value) {
        Object type = schema.get("type");
        if ("object".equals(type)) {
            if (!(value instanceof Map<?, ?> map)) {
                return false;
            }
            Object properties = schema.get("properties");
            Map<?, ?> allowedProperties = properties instanceof Map<?, ?> propertyMap ? propertyMap : Map.of();
            if (Boolean.FALSE.equals(schema.get("additionalProperties"))
                    && !map.keySet().stream().allMatch(allowedProperties::containsKey)) {
                return false;
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                Object nestedSchema = allowedProperties.get(entry.getKey());
                if (nestedSchema instanceof Map<?, ?> nestedSchemaMap
                        && !valueAllowedBySchema((Map<String, Object>) nestedSchemaMap, entry.getValue())) {
                    return false;
                }
            }
            return true;
        }
        if ("string".equals(type)) {
            return value instanceof String;
        }
        if ("integer".equals(type)) {
            return value instanceof Integer || value instanceof Long;
        }
        if ("number".equals(type)) {
            return value instanceof Number;
        }
        if ("boolean".equals(type)) {
            return value instanceof Boolean;
        }
        return true;
    }

    private static boolean containsProviderShapedArgument(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (providerShapedKey(entry.getKey()) || containsProviderShapedArgument(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (containsProviderShapedArgument(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean providerShapedKey(Object key) {
        if (!(key instanceof String stringKey)) {
            return false;
        }
        String normalized = stringKey.strip().toLowerCase();
        return normalized.equals("providerpayload")
                || normalized.equals("rawpayload")
                || normalized.equals("rawproviderpayload")
                || normalized.equals("providerurl")
                || normalized.equals("secret")
                || normalized.equals("secretref")
                || normalized.equals("secretref.value")
                || normalized.equals("secrettoken")
                || normalized.equals("accesstoken")
                || normalized.equals("access_token")
                || normalized.equals("token")
                || normalized.equals("all")
                || normalized.equals("*")
                || normalized.startsWith("provider")
                || normalized.startsWith("secret")
                || normalized.contains("payload")
                || normalized.contains("token");
    }

    public record QwenMcpToolRequest(
            String channelId,
            String modelRef,
            String toolName,
            String userRef,
            String runtimeProfileHash,
            String runtimeProfileVersion,
            String runtimeProfileUserRef,
            String runtimeProfileSignature,
            boolean runtimeProfileRevoked,
            String runtimeTokenExpiresAt,
            boolean consentGranted,
            List<String> grantedCapabilities,
            List<String> scopedToolGrants,
            Map<String, Object> input) {
        public QwenMcpToolRequest {
            channelId = channelId == null || channelId.isBlank() ? "channels.weave-chat" : channelId;
            modelRef = modelRef == null || modelRef.isBlank() ? "lmstudio/qwen/qwen3.5-9b" : modelRef;
            runtimeProfileVersion = runtimeProfileVersion == null || runtimeProfileVersion.isBlank()
                    ? RUNTIME_PROFILE_VERSION
                    : runtimeProfileVersion;
            runtimeProfileUserRef = runtimeProfileUserRef == null || runtimeProfileUserRef.isBlank() ? userRef : runtimeProfileUserRef;
            runtimeProfileSignature = runtimeProfileSignature == null ? "" : runtimeProfileSignature;
            runtimeTokenExpiresAt = runtimeTokenExpiresAt == null || runtimeTokenExpiresAt.isBlank()
                    ? Instant.now().plusSeconds(120).toString()
                    : runtimeTokenExpiresAt;
            grantedCapabilities = List.copyOf(grantedCapabilities == null ? List.of() : grantedCapabilities);
            scopedToolGrants = List.copyOf(scopedToolGrants == null ? List.of() : scopedToolGrants);
            input = Map.copyOf(input == null ? Map.of() : input);
        }
    }

    public record QwenMcpToolTurn(
            boolean allowed,
            String decision,
            String modelVisibleToolResult,
            Map<String, Object> supportSafeEvidence,
            Map<String, Object> toolResult) {
    }
}
