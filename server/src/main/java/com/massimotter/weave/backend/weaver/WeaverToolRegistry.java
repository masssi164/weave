package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.contract.mcp.MemberMcpToolCatalog;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class WeaverToolRegistry {

    private final AuditEventPublisher auditEventPublisher;
    private final Map<String, WeaverDomainToolDefinition> definitions;

    public WeaverToolRegistry(AuditEventPublisher auditEventPublisher) {
        this.auditEventPublisher = auditEventPublisher;
        this.definitions = initialDefinitions();
    }

    public List<WeaverDomainToolDefinition> discover(List<String> grantedCapabilities) {
        List<String> safeCapabilities = grantedCapabilities == null ? List.of() : grantedCapabilities;
        return definitions.values().stream()
                .filter(definition -> safeCapabilities.contains(definition.requiredCapability()))
                .toList();
    }

    public WeaverToolInvocationResult invoke(WeaverToolInvocationRequest request) {
        WeaverDomainToolDefinition definition = definitions.get(request.toolName());
        String governanceDenial = governanceDenial(request, definition);
        if (governanceDenial != null) {
            audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), governanceDenial, Map.of(
                    "reason", governanceDenial,
                    "auditRef", auditRef(request.toolName(), governanceDenial)));
            return blocked(request.toolName(), governanceDenial, "Weaver tool invocation failed closed before provider access.");
        }
        if (definition == null || !request.grantedCapabilities().contains(definition.requiredCapability())) {
            audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "blocked", Map.of(
                    "reason", "not_granted",
                    "auditRef", auditRef(request.toolName(), "blocked")));
            return blocked(request.toolName(), "blocked", "Tool is not available for this runtime profile.");
        }
        if (!request.scopedToolGrants().contains(request.toolName())) {
            audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "scoped_grant_missing", Map.of(
                    "reason", "scoped_tool_grant_missing",
                    "requiredTool", request.toolName(),
                    "auditRef", auditRef(request.toolName(), "scoped_grant_missing")));
            return blocked(request.toolName(), "scoped_grant_missing", "Tool is not included in the signed scoped grant.");
        }
        boolean approvalReceiptValidated = false;
        if (definition.writeLike()) {
            WeaverApprovalReceipt approvalReceipt = request.approvalReceipt();
            if (approvalReceipt == null || !approvalReceipt.validFor(request.userRef(), request.toolName())) {
                audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "approval_required", Map.of(
                        "domain", definition.domain(),
                        "approvalReceiptValidated", false,
                        "auditRef", auditRef(request.toolName(), "approval_required")));
                return new WeaverToolInvocationResult(
                        request.toolName(),
                        "approval_required",
                        true,
                        true,
                        Map.of(
                                "approvalPolicy", definition.approvalRequirement().name(),
                                "approvalReceiptValidated", false,
                                "auditRef", auditRef(request.toolName(), "approval_required")),
                        "This action requires a valid approval receipt before Weaver may continue.");
            }
            approvalReceiptValidated = true;
        }
        audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "invoked", Map.of(
                "domain", definition.domain(),
                "mode", definition.mode().name(),
                "consentGranted", true,
                "approvalReceiptRef", request.approvalReceiptRef() == null ? "none" : request.approvalReceiptRef(),
                "approvalReceiptAuditRef", request.approvalReceipt() == null ? "none" : request.approvalReceipt().auditRef(),
                "approvalReceiptPolicyVersion", request.approvalReceipt() == null ? "none" : request.approvalReceipt().policyVersion(),
                "approvalReceiptValidated", approvalReceiptValidated,
                "auditRef", auditRef(request.toolName(), "invoked")));
        return new WeaverToolInvocationResult(
                request.toolName(),
                "ok",
                false,
                true,
                successResult(request, definition),
                "Tool invocation went through a Weave domain capability boundary; raw provider APIs are not exposed.");
    }

    private Map<String, Object> successResult(WeaverToolInvocationRequest request, WeaverDomainToolDefinition definition) {
        Map<String, Object> base = new LinkedHashMap<>();
        base.put("domain", definition.domain());
        base.put("canonicalRefs", canonicalRefs(request.input()));
        base.put("approvalReceiptAuditRef", request.approvalReceipt() == null ? "none" : request.approvalReceipt().auditRef());
        base.put("rawProviderPayload", "redacted");
        base.put("auditRef", auditRef(request.toolName(), "invoked"));
        if ("identity.read".equals(definition.name())) {
            base.put("identity", Map.of(
                    "userRef", request.userRef(),
                    "runtimeProfileHash", request.runtimeProfileHash(),
                    "supportSafe", true,
                    "rawClaimsExposed", false));
        } else if ("registry.tools.read".equals(definition.name())) {
            base.put("tools", definitions.values().stream()
                    .filter(tool -> request.grantedCapabilities().contains(tool.requiredCapability()))
                    .filter(tool -> request.scopedToolGrants().contains(tool.name()))
                    .map(tool -> Map.of(
                            "name", tool.name(),
                            "mode", tool.mode().name(),
                            "domain", tool.domain(),
                            "approvalRequirement", tool.approvalRequirement().name()))
                    .toList());
        } else {
            base.put("result", "support-safe-placeholder");
        }
        return Map.copyOf(base);
    }

    private String governanceDenial(WeaverToolInvocationRequest request, WeaverDomainToolDefinition definition) {
        if (!request.runtimeProfileSignature().startsWith("weave-signature:v1:")) {
            return "runtime_profile_unsigned";
        }
        if (!request.userRef().equals(request.runtimeProfileUserRef())) {
            return "runtime_profile_user_mismatch";
        }
        if (request.runtimeProfileRevoked()) {
            return "runtime_profile_revoked";
        }
        if (runtimeTokenExpired(request.runtimeTokenExpiresAt())) {
            return "runtime_token_expired";
        }
        if (!request.consentGranted()) {
            return "consent_required";
        }
        if (request.scopedToolGrants().stream().anyMatch(this::overbroadGrant)) {
            return "overbroad_grant";
        }
        if (definition != null && request.scopedToolGrants().stream()
                .anyMatch(grant -> grant.startsWith(definition.domain() + ".") || grant.endsWith(".*"))) {
            return "overbroad_grant";
        }
        return null;
    }

    private boolean runtimeTokenExpired(String runtimeTokenExpiresAt) {
        try {
            return runtimeTokenExpiresAt == null || runtimeTokenExpiresAt.isBlank()
                    || !Instant.parse(runtimeTokenExpiresAt).isAfter(Instant.now());
        } catch (DateTimeParseException ignored) {
            return true;
        }
    }

    private boolean overbroadGrant(String grant) {
        if (grant == null) {
            return false;
        }
        String normalized = grant.strip().toLowerCase();
        return normalized.equals("*")
                || normalized.equals("all")
                || normalized.contains("provider")
                || normalized.endsWith(".*");
    }

    private WeaverToolInvocationResult blocked(String toolName, String status, String message) {
        return new WeaverToolInvocationResult(
                toolName,
                status,
                false,
                true,
                Map.of("auditRef", auditRef(toolName, status)),
                message);
    }

    private Map<String, Object> canonicalRefs(Map<String, Object> input) {
        Map<String, Object> refs = new LinkedHashMap<>();
        copyCanonicalRef(input, refs, "spaceRef", "space");
        copyCanonicalRef(input, refs, "decisionRef", "decision");
        copyCanonicalRef(input, refs, "boardTaskRef", "boardTask");
        return Map.copyOf(refs);
    }

    private void copyCanonicalRef(Map<String, Object> input, Map<String, Object> refs, String inputKey, String outputKey) {
        Object value = input.get(inputKey);
        if (value instanceof String ref && canonicalRef(ref)) {
            refs.put(outputKey, ref);
        }
    }

    private boolean canonicalRef(String ref) {
        return ref.startsWith("space:") || ref.startsWith("decision:") || ref.startsWith("board-task:");
    }

    private String auditRef(String toolName, String status) {
        return "audit://weaver-tool/" + (toolName == null || toolName.isBlank() ? "unknown" : toolName) + "/" + status;
    }

    private void audit(String userRef, String runtimeProfileHash, String toolName, String status, Map<String, Object> payload) {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload);
        String safeUserRef = userRef == null || userRef.isBlank() ? "user:unknown" : userRef;
        safePayload.putIfAbsent("runtimeProfileHash", runtimeProfileHash);
        safePayload.putIfAbsent("user", safeUserRef);
        safePayload.put("toolName", toolName);
        safePayload.putIfAbsent("tool", toolName);
        safePayload.putIfAbsent("action", "tool.invoke");
        safePayload.putIfAbsent("domain", "weaver-runtime");
        safePayload.putIfAbsent("providerRef", "provider:domain-facade");
        safePayload.putIfAbsent("credentialRef", "credentialref://weave/runtime/short-lived");
        safePayload.putIfAbsent("decision", status);
        safePayload.put("status", status);
        safePayload.put("supportSafe", true);
        auditEventPublisher.publish(new AuditEvent(
                "tenant:workspace",
                null,
                safeUserRef,
                "weaver-tool-registry",
                AuditAction.WEAVER_TOOL_INVOCATION_RECORDED,
                Instant.now(),
                "weaver-tool:" + toolName,
                AuditRedactionLevel.SUPPORT_SAFE,
                safePayload));
    }

    private Map<String, WeaverDomainToolDefinition> initialDefinitions() {
        Map<String, WeaverDomainToolDefinition> registry = new LinkedHashMap<>();
        MemberMcpToolCatalog.tools().forEach(tool -> add(registry, new WeaverDomainToolDefinition(
                tool.name(),
                tool.version(),
                tool.domain(),
                toWeaverMode(tool.mode()),
                tool.requiredCapability(),
                tool.approvalRequired() ? WeaverApprovalRequirement.REQUIRED_BEFORE_INVOCATION : WeaverApprovalRequirement.NONE,
                tool.inputSchema(),
                List.of("providerCredentials", "rawProviderPayload", "secretRef.value"),
                tool.description())));
        return Collections.unmodifiableMap(registry);
    }

    private void add(Map<String, WeaverDomainToolDefinition> registry, WeaverDomainToolDefinition definition) {
        registry.put(definition.name(), definition);
    }

    private WeaverToolMode toWeaverMode(com.massimotter.weave.contract.mcp.MemberMcpToolMode mode) {
        return switch (mode) {
            case READ -> WeaverToolMode.READ;
            case WRITE -> WeaverToolMode.WRITE;
            case EXTERNAL_SEND -> WeaverToolMode.EXTERNAL_SEND;
        };
    }
}
