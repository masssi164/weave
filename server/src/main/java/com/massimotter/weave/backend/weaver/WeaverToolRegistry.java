package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
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
        if (definition.writeLike() && (request.approvalReceiptRef() == null || request.approvalReceiptRef().isBlank())) {
            audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "approval_required", Map.of(
                    "domain", definition.domain(),
                    "auditRef", auditRef(request.toolName(), "approval_required")));
            return new WeaverToolInvocationResult(
                    request.toolName(),
                    "approval_required",
                    true,
                    true,
                    Map.of(
                            "approvalPolicy", definition.approvalRequirement().name(),
                            "auditRef", auditRef(request.toolName(), "approval_required")),
                    "This action requires an approval receipt before Weaver may continue.");
        }
        audit(request.userRef(), request.runtimeProfileHash(), request.toolName(), "invoked", Map.of(
                "domain", definition.domain(),
                "mode", definition.mode().name(),
                "consentGranted", true,
                "approvalReceiptRef", request.approvalReceiptRef() == null ? "none" : request.approvalReceiptRef(),
                "auditRef", auditRef(request.toolName(), "invoked")));
        return new WeaverToolInvocationResult(
                request.toolName(),
                "ok",
                false,
                true,
                Map.of(
                        "domain", definition.domain(),
                        "result", "support-safe-placeholder",
                        "canonicalRefs", canonicalRefs(request.input()),
                        "rawProviderPayload", "redacted",
                        "auditRef", auditRef(request.toolName(), "invoked")),
                "Tool invocation went through a Weave domain capability boundary; raw provider APIs are not exposed.");
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
        add(registry, tool("calendar.search_events", "calendar-events", WeaverToolMode.READ, "weaver.calendar_read", WeaverApprovalRequirement.NONE));
        add(registry, tool("boards.search_tasks", "boards-tasks", WeaverToolMode.READ, "weaver.boards_read", WeaverApprovalRequirement.NONE));
        add(registry, tool("files.search", "files-docs", WeaverToolMode.READ, "weaver.files_read", WeaverApprovalRequirement.NONE));
        add(registry, tool("chat.search_messages", "chat-channels", WeaverToolMode.READ, "weaver.chat_read", WeaverApprovalRequirement.NONE));
        add(registry, tool("notifications.create_action_request", "notifications", WeaverToolMode.EXTERNAL_SEND, "weaver.notifications_write", WeaverApprovalRequirement.REQUIRED_BEFORE_INVOCATION));
        add(registry, tool("boards.comment", "boards-tasks", WeaverToolMode.WRITE, "weaver.boards_write", WeaverApprovalRequirement.REQUIRED_BEFORE_INVOCATION));
        return Collections.unmodifiableMap(registry);
    }

    private void add(Map<String, WeaverDomainToolDefinition> registry, WeaverDomainToolDefinition definition) {
        registry.put(definition.name(), definition);
    }

    private WeaverDomainToolDefinition tool(
            String name,
            String domain,
            WeaverToolMode mode,
            String requiredCapability,
            WeaverApprovalRequirement approvalRequirement) {
        return new WeaverDomainToolDefinition(
                name,
                "v1",
                domain,
                mode,
                requiredCapability,
                approvalRequirement,
                Map.of(
                        "type", "object",
                        "additionalProperties", false,
                        "description", "Validated by the Weave " + domain + " facade before provider access."),
                List.of("providerCredentials", "rawProviderPayload", "secretRef.value"),
                "Weaver domain tool exposed only through Weave capability grants.");
    }
}
