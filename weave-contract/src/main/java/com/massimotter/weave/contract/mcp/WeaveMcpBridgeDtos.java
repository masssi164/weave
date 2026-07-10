package com.massimotter.weave.contract.mcp;

import java.util.List;
import java.util.Map;

public final class WeaveMcpBridgeDtos {
    private WeaveMcpBridgeDtos() {}

    public record WeaveMcpKey(String value) {
        public WeaveMcpKey { value = WeaveMcpTypes.text(value, "value"); }
    }

    public record WeaveMcpRef(String value) {
        public WeaveMcpRef { value = WeaveMcpTypes.text(value, "value"); }
    }

    public record ApprovalEvidence(
            String protocol,
            String evidenceRef,
            String toolName,
            List<String> scopeRefs,
            String decision,
            String decidedAt) {
        public ApprovalEvidence {
            protocol = WeaveMcpTypes.text(protocol, "protocol");
            evidenceRef = WeaveMcpTypes.text(evidenceRef, "evidenceRef");
            toolName = WeaveMcpTypes.text(toolName, "toolName");
            scopeRefs = WeaveMcpTypes.copyStrings(scopeRefs);
            decision = WeaveMcpTypes.text(decision, "decision");
            decidedAt = WeaveMcpTypes.text(decidedAt, "decidedAt");
            if (!"mcp-elicitation/v1".equals(protocol)) {
                throw new IllegalArgumentException("unsupported MCP approval evidence protocol");
            }
            if (!evidenceRef.startsWith("elicitation://")) {
                throw new IllegalArgumentException("evidenceRef must start with elicitation://");
            }
            if (!List.of("allow-once", "allow-always").contains(decision)) {
                throw new IllegalArgumentException("decision must be allow-once or allow-always");
            }
        }
    }

    public record RuntimeInvocationContext(
            WeaveMcpRef orgRef,
            WeaveMcpRef userRef,
            WeaveMcpRef runtimeProfileRef,
            String runtimeProfileHash,
            WeaveMcpRef runtimeTokenRef,
            String auditRef,
            List<String> capabilityGrants,
            List<String> allowedTools) {

        public RuntimeInvocationContext {
            if (orgRef == null) throw new IllegalArgumentException("orgRef must not be null");
            if (userRef == null) throw new IllegalArgumentException("userRef must not be null");
            runtimeProfileHash = WeaveMcpTypes.text(runtimeProfileHash, "runtimeProfileHash");
            auditRef = WeaveMcpTypes.text(auditRef, "auditRef");
            capabilityGrants = WeaveMcpTypes.copyStrings(capabilityGrants);
            allowedTools = WeaveMcpTypes.copyStrings(allowedTools);
        }
    }

    public enum ToolInvocationStatus {
        SUCCESS,
        APPROVAL_REQUIRED,
        DENIED,
        VALIDATION_ERROR,
        UNAVAILABLE,
        PROVIDER_FAILURE
    }

    public record WeaveMcpContentBlock(String type, String text, WeaveMcpRef ref, Map<String, Object> metadata) {
        public WeaveMcpContentBlock {
            type = WeaveMcpTypes.text(type, "type");
            text = text == null ? "" : text;
            metadata = WeaveMcpTypes.copyMap(metadata);
        }
    }

    public record WeaveMcpToolAnnotations(boolean readOnlyHint, boolean destructiveHint, boolean openWorldHint) {}

    public record WeaveMcpToolDefinition(
            String name,
            String version,
            String domain,
            WeaveMcpToolMode mode,
            String requiredCapability,
            boolean approvalRequired,
            Map<String, Object> inputSchema,
            WeaveMcpToolAnnotations annotations,
            String description) {

        public WeaveMcpToolDefinition {
            name = WeaveMcpTypes.text(name, "name");
            version = WeaveMcpTypes.text(version, "version");
            domain = WeaveMcpTypes.text(domain, "domain");
            requiredCapability = WeaveMcpTypes.text(requiredCapability, "requiredCapability");
            if (mode == null) throw new IllegalArgumentException("mode must not be null");
            inputSchema = WeaveMcpTypes.copyMap(inputSchema == null || inputSchema.isEmpty() ? Map.of("type", "object") : inputSchema);
            annotations = annotations == null ? new WeaveMcpToolAnnotations(mode == WeaveMcpToolMode.READ, false, false) : annotations;
            description = description == null || description.isBlank() ? name + " via Weave server policy boundary." : description.trim();
        }

        public boolean writeLike() { return mode.approvalRequiredByDefault(); }
    }

    public record WeaveMcpToolCatalog(String serverNamespace, String contractVersion, List<WeaveMcpToolDefinition> tools) {
        public WeaveMcpToolCatalog {
            serverNamespace = WeaveMcpTypes.text(serverNamespace, "serverNamespace");
            contractVersion = WeaveMcpTypes.text(contractVersion, "contractVersion");
            tools = List.copyOf(tools == null ? List.of() : tools);
        }
    }

    public record BridgeDiscoveryRequest(RuntimeInvocationContext runtime) {
        public BridgeDiscoveryRequest { if (runtime == null) throw new IllegalArgumentException("runtime must not be null"); }
    }

    public record BridgeDiscoveryResponse(RuntimeInvocationContext runtime, WeaveMcpToolCatalog catalog) {
        public BridgeDiscoveryResponse {
            if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
            if (catalog == null) throw new IllegalArgumentException("catalog must not be null");
        }
    }

    public record BridgeInvocationRequest(
            String toolName,
            Map<String, Object> arguments,
            RuntimeInvocationContext runtime,
            ApprovalEvidence approvalEvidence) {
        public BridgeInvocationRequest {
            toolName = WeaveMcpTypes.text(toolName, "toolName");
            arguments = WeaveMcpTypes.copyMap(arguments);
            if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        }

        public BridgeInvocationRequest(String toolName, Map<String, Object> arguments, RuntimeInvocationContext runtime) {
            this(toolName, arguments, runtime, null);
        }
    }

    public record BridgeInvocationResponse(
            String toolName,
            ToolInvocationStatus status,
            String auditRef,
            boolean supportSafe,
            List<WeaveMcpContentBlock> content,
            Map<String, Object> structuredContent) {
        public BridgeInvocationResponse {
            toolName = WeaveMcpTypes.text(toolName, "toolName");
            if (status == null) throw new IllegalArgumentException("status must not be null");
            auditRef = WeaveMcpTypes.text(auditRef, "auditRef");
            content = List.copyOf(content == null ? List.of() : content);
            structuredContent = WeaveMcpTypes.copyMap(structuredContent);
        }
    }
}
