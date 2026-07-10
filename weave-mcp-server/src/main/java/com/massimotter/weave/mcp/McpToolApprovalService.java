package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.ai.mcp.annotation.context.StructuredElicitResult;
import org.springframework.stereotype.Component;

@Component
final class McpToolApprovalService {

    ApprovalEvidence requireApproval(
            McpSyncRequestContext context,
            String toolName,
            String title,
            String description,
            Map<String, Object> arguments) {
        if (context == null || !context.elicitEnabled()) {
            throw new McpBoundaryException("mcp-elicitation-unavailable");
        }
        List<String> scopes = canonicalScopeRefs(arguments);
        String canonicalScope = scopes.isEmpty() ? "workspace" : String.join(",", scopes);
        StructuredElicitResult<ApprovalForm> result = context.elicit(
                spec -> spec.message(description)
                        .meta(Map.of(
                                "codex_approval_kind", "mcp_tool_call",
                                "connector_name", "Weave",
                                "tool_name", toolName,
                                "tool_title", title,
                                "tool_description", description,
                                "tool_params_display", canonicalScope,
                                "canonical_scope", canonicalScope)),
                ApprovalForm.class);
        ApprovalForm form = result.structuredContent();
        if (result.action() != McpSchema.ElicitResult.Action.ACCEPT || form == null || !form.approved()) {
            throw new McpBoundaryException("mcp-elicitation-declined");
        }
        Object rawDecision = result.meta() == null
                ? null
                : result.meta().get("openclaw_approval_decision");
        String decision = "allow-always".equals(rawDecision) || form.remember()
                ? "allow-always"
                : "allow-once";
        return new ApprovalEvidence(
                "mcp-elicitation/v1",
                "elicitation://openclaw/" + UUID.randomUUID(),
                toolName,
                scopes,
                decision,
                Instant.now().toString());
    }

    static List<String> canonicalScopeRefs(Map<String, Object> arguments) {
        List<String> refs = new ArrayList<>();
        for (String key : List.of(
                "spaceRef",
                "channelRef",
                "threadRef",
                "decisionRef",
                "boardTaskRef",
                "taskRef",
                "calendarRef",
                "eventRef",
                "messageRef",
                "fileRef")) {
            Object value = arguments == null ? null : arguments.get(key);
            if (value instanceof String ref && canonicalRef(ref)) {
                refs.add(ref);
            }
        }
        return refs.stream().distinct().sorted().toList();
    }

    private static boolean canonicalRef(String ref) {
        return ref.startsWith("space:")
                || ref.startsWith("channel:")
                || ref.startsWith("thread:")
                || ref.startsWith("decision:")
                || ref.startsWith("board-task:")
                || ref.startsWith("task:")
                || ref.startsWith("calendar:")
                || ref.startsWith("event:")
                || ref.startsWith("message:")
                || ref.startsWith("file:");
    }

    record ApprovalForm(boolean approved, boolean remember) {
    }
}
