package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.model.WeaverRuntimeProfileResponse;
import com.massimotter.weave.backend.weaver.MemberDomainToolDispatcher;
import com.massimotter.weave.backend.weaver.WeaverApprovalReceipt;
import com.massimotter.weave.backend.weaver.WeaverToolInvocationRequest;
import com.massimotter.weave.backend.weaver.WeaverToolInvocationResult;
import com.massimotter.weave.backend.weaver.WeaverToolRegistry;
import com.massimotter.weave.contract.mcp.MemberMcpDomainDefinition;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalReceiptRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationRequest;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.RuntimeInvocationContext;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpContentBlock;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpRef;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.WeaveMcpToolCatalog;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class WeaverMcpBridgeService {

    private static final String MEMBER_SERVER_KEY = MemberMcpToolCatalog.SERVER_NAMESPACE;
    private static final List<String> FORBIDDEN_TOOL_PREFIXES = List.of(
            "admin.",
            "provider.",
            "migration.",
            "policy.",
            "credentials.",
            "control-room.",
            "readiness.",
            "admin-runtime.");

    private final WeaverRuntimeService runtimeService;
    private final WeaverToolRegistry toolRegistry;
    private final MemberDomainToolDispatcher memberDomainToolDispatcher;

    public WeaverMcpBridgeService(
            WeaverRuntimeService runtimeService,
            WeaverToolRegistry toolRegistry,
            MemberDomainToolDispatcher memberDomainToolDispatcher) {
        this.runtimeService = runtimeService;
        this.toolRegistry = toolRegistry;
        this.memberDomainToolDispatcher = memberDomainToolDispatcher;
    }

    public BridgeDiscoveryResponse discoverMcpTools(Jwt jwt, String runtimeProfileHash, String serverKey) {
        WeaverRuntimeProfileResponse profile = runtimeService.profileByHash(jwt, runtimeProfileHash);
        RuntimeInvocationContext runtime = runtimeContext(profile, serverKey, null, discoveryAuditRef(serverKey));
        if (!profile.enabled() || !MEMBER_SERVER_KEY.equals(serverKey)) {
            return new BridgeDiscoveryResponse(runtime, new WeaveMcpToolCatalog(serverKey, MemberMcpDomainDefinition.CONTRACT_VERSION, List.of()));
        }
        return new BridgeDiscoveryResponse(
                runtime,
                new WeaveMcpToolCatalog(
                        MEMBER_SERVER_KEY,
                        MemberMcpDomainDefinition.CONTRACT_VERSION,
                        MemberMcpToolCatalog.serverExecutableTools().stream()
                                .filter(definition -> profile.allowedCapabilities().contains(definition.requiredCapability()))
                                .filter(definition -> profile.toolAllowlist().contains(definition.name()))
                                .filter(definition -> !forbiddenToolName(definition.name()))
                                .map(definition -> definition.asBridgeDefinition())
                                .toList()));
    }

    public BridgeInvocationResponse invokeMcpTool(
            Jwt jwt,
            String serverKey,
            String toolName,
            BridgeInvocationRequest request) {
        WeaverRuntimeProfileResponse profile = runtimeService.profileByHash(jwt, request.runtime().runtimeProfileHash());
        if (!profile.enabled()
                || !MEMBER_SERVER_KEY.equals(serverKey)
                || forbiddenToolName(toolName)
                || !MemberMcpToolCatalog.byName().containsKey(toolName)
                || !MemberMcpToolCatalog.byName().get(toolName).serverExecutable()) {
            return bridgeInvocationResponse(
                    toolName,
                    ToolInvocationStatus.DENIED,
                    auditRef(toolName, "member_mcp_tool_forbidden"),
                    "MCP member tool invocation failed closed.",
                    Map.of("supportSafe", true, "approvalRequired", false));
        }
        if (!toolName.equals(request.toolName())) {
            return bridgeInvocationResponse(
                    toolName,
                    ToolInvocationStatus.VALIDATION_ERROR,
                    auditRef(toolName, "tool_name_mismatch"),
                    "Tool name in request body must match the URL path.",
                    Map.of("supportSafe", true, "requestedToolName", request.toolName(), "approvalRequired", false));
        }

        WeaverToolInvocationResult governance = toolRegistry.invoke(new WeaverToolInvocationRequest(
                toolName,
                profile.userRef(),
                profile.runtimeProfileHash(),
                profile.userRef(),
                profile.signature(),
                profile.revoked(),
                String.valueOf(profile.supportSafeProfileReceipt().getOrDefault("runtimeTokenExpiresAt", "")),
                true,
                profile.allowedCapabilities(),
                profile.toolAllowlist(),
                request.arguments(),
                request.runtime().approvalReceiptRef() == null ? null : request.runtime().approvalReceiptRef().value(),
                approvalReceipt(request)));
        if (!"ok".equals(governance.status())) {
            return bridgeInvocationResponse(
                    governance.toolName(),
                    toInvocationStatus(governance.status()),
                    String.valueOf(governance.redactedResult().getOrDefault("auditRef", auditRef(toolName, governance.status()))),
                    governance.supportSafeMessage(),
                    withBridgeFields(governance.redactedResult(), governance.approvalRequired(), governance.supportSafeMessage()));
        }

        Map<String, Object> structuredContent = memberDomainToolDispatcher.dispatch(toolName, request.arguments());
        if (!"ok".equals(structuredContent.get("status"))) {
            return bridgeInvocationResponse(
                    governance.toolName(),
                    ToolInvocationStatus.DENIED,
                    String.valueOf(structuredContent.getOrDefault("auditRef", auditRef(toolName, "dispatch_blocked"))),
                    "MCP member tool dispatch failed closed before provider access.",
                    withBridgeFields(structuredContent, false, "MCP member tool dispatch failed closed before provider access."));
        }
        return bridgeInvocationResponse(
                governance.toolName(),
                ToolInvocationStatus.SUCCESS,
                String.valueOf(structuredContent.getOrDefault("auditRef", governance.redactedResult().getOrDefault("auditRef", auditRef(toolName, "invoked")))),
                governance.supportSafeMessage(),
                withBridgeFields(structuredContent, false, governance.supportSafeMessage()));
    }

    private Map<String, Object> withBridgeFields(Map<String, Object> structuredContent, boolean approvalRequired, String supportSafeMessage) {
        return Map.ofEntries(
                Map.entry("status", String.valueOf(structuredContent.getOrDefault("status", "ok"))),
                Map.entry("supportSafe", true),
                Map.entry("approvalRequired", approvalRequired),
                Map.entry("redactedContent", structuredContent),
                Map.entry("supportSafeMessage", supportSafeMessage),
                Map.entry("structuredContent", structuredContent));
    }

    private WeaverApprovalReceipt approvalReceipt(BridgeInvocationRequest request) {
        ApprovalReceiptRef ref = request.runtime().approvalReceiptRef();
        if (ref == null) {
            return null;
        }
        return new WeaverApprovalReceipt(
                ref.value(),
                request.runtime().userRef().value(),
                request.toolName(),
                List.of(request.runtime().runtimeProfileRef().value()),
                "support-safe-bridge-v1",
                Instant.now().plus(5, ChronoUnit.MINUTES).toString(),
                request.runtime().auditRef());
    }

    private BridgeInvocationResponse bridgeInvocationResponse(String toolName, ToolInvocationStatus status, String auditRef, String message, Map<String, Object> structuredContent) {
        return new BridgeInvocationResponse(
                toolName,
                status,
                auditRef,
                true,
                List.of(new WeaveMcpContentBlock("text", message, null, Map.of("status", status.name()))),
                structuredContent);
    }

    private RuntimeInvocationContext runtimeContext(
            WeaverRuntimeProfileResponse profile,
            String serverKey,
            ApprovalReceiptRef approvalReceiptRef,
            String auditRef) {
        return new RuntimeInvocationContext(
                new WeaveMcpRef("org:workspace"),
                new WeaveMcpRef(profile.userRef()),
                new WeaveMcpRef("weave-runtime-profile://" + profile.runtimeProfileHash()),
                profile.runtimeProfileHash(),
                new WeaveMcpRef(String.valueOf(serverProjectionRuntimeTokenRef(profile, serverKey))),
                auditRef,
                approvalReceiptRef,
                null,
                profile.allowedCapabilities(),
                profile.toolAllowlist());
    }

    private Object serverProjectionRuntimeTokenRef(WeaverRuntimeProfileResponse profile, String serverKey) {
        Object servers = profile.mcpProjection().get("servers");
        if (servers instanceof Map<?, ?> serverMap) {
            Object server = serverMap.get(serverKey);
            if (server instanceof Map<?, ?> serverProjection) {
                Object runtimeTokenRef = serverProjection.get("runtimeTokenRef");
                return runtimeTokenRef == null ? "credentialref://weave/runtime/short-lived/unknown" : runtimeTokenRef;
            }
        }
        return "credentialref://weave/runtime/short-lived/unknown";
    }

    private boolean forbiddenToolName(String toolName) {
        String normalized = toolName == null ? "" : toolName.strip().toLowerCase();
        return FORBIDDEN_TOOL_PREFIXES.stream().anyMatch(normalized::startsWith)
                || normalized.contains("provider")
                || normalized.contains("credential")
                || normalized.contains("control-room")
                || normalized.contains("admin-runtime")
                || normalized.contains("readiness");
    }

    private String discoveryAuditRef(String serverKey) {
        return "audit://weaver-mcp/" + serverKey + "/discover";
    }

    private String auditRef(String toolName, String status) {
        return "audit://weaver-tool/" + (toolName == null || toolName.isBlank() ? "unknown" : toolName) + "/" + status;
    }

    private ToolInvocationStatus toInvocationStatus(String status) {
        return switch (status) {
            case "ok" -> ToolInvocationStatus.SUCCESS;
            case "approval_required", "blocked", "scoped_grant_missing", "runtime_profile_fetch_denied", "runtime_profile_unsigned", "runtime_profile_user_mismatch", "runtime_profile_revoked", "runtime_token_expired", "consent_required", "overbroad_grant" -> ToolInvocationStatus.DENIED;
            case "tool_name_mismatch" -> ToolInvocationStatus.VALIDATION_ERROR;
            default -> ToolInvocationStatus.UNAVAILABLE;
        };
    }
}
