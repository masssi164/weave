package com.massimotter.weave.backend.weaver;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import java.time.Instant;
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
        if (definition == null || !request.grantedCapabilities().contains(definition.requiredCapability())) {
            audit(request.userRef(), request.toolName(), "blocked", Map.of("reason", "not_granted"));
            return new WeaverToolInvocationResult(
                    request.toolName(),
                    "blocked",
                    false,
                    true,
                    Map.of(),
                    "Tool is not available for this runtime profile.");
        }
        if (definition.writeLike() && (request.approvalReceiptRef() == null || request.approvalReceiptRef().isBlank())) {
            audit(request.userRef(), request.toolName(), "approval_required", Map.of("domain", definition.domain()));
            return new WeaverToolInvocationResult(
                    request.toolName(),
                    "approval_required",
                    true,
                    true,
                    Map.of("approvalPolicy", definition.approvalRequirement().name()),
                    "This action requires an approval receipt before Weaver may continue.");
        }
        audit(request.userRef(), request.toolName(), "invoked", Map.of("domain", definition.domain(), "mode", definition.mode().name()));
        return new WeaverToolInvocationResult(
                request.toolName(),
                "ok",
                false,
                true,
                Map.of(
                        "domain", definition.domain(),
                        "result", "support-safe-placeholder",
                        "rawProviderPayload", "redacted"),
                "Tool invocation went through a Weave domain capability boundary; raw provider APIs are not exposed.");
    }

    private void audit(String userRef, String toolName, String status, Map<String, Object> payload) {
        Map<String, Object> safePayload = new LinkedHashMap<>(payload);
        String safeUserRef = userRef == null || userRef.isBlank() ? "user:unknown" : userRef;
        safePayload.putIfAbsent("runtimeProfileHash", "sha256:profile-hash-required-by-runtime-profile");
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
