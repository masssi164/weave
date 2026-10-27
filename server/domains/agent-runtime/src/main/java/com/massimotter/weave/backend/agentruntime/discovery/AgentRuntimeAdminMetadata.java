package com.massimotter.weave.backend.agentruntime.discovery;

import com.massimotter.weave.backend.admin.api.McpServerBindingResponse;
import java.util.List;

/** Existing disabled future MCP preview; this declaration never registers an executable tool. */
public final class AgentRuntimeAdminMetadata {
    private AgentRuntimeAdminMetadata() {}

    public static McpServerBindingResponse mcpBinding() {
        return new McpServerBindingResponse(
                "weave-domain-tools",
                "Weave governed domain tools",
                "streamable-http",
                "internal://weave-mcp/streamable-http",
                "credentialref://weave/mcp/weave-domain-tools/runtime-token",
                List.of(
                        "admin.get_readiness",
                        "weaver.get_runtime_profile_projection",
                        "calendar.search_events",
                        "boards.comment"),
                List.of(
                        "weaver.admin_readiness_read",
                        "weaver.runtime_profile_read",
                        "weaver.calendar_read",
                        "weaver.boards_write"),
                true,
                false,
                "disabled-by-default",
                true,
                false,
                false,
                false,
                List.of("audit://weaver/mcp/weave-domain-tools/binding-preview"),
                List.of(
                        "Enable only after org policy, runtime grants, Streamable HTTP auth, and approval receipts are configured."));
    }
}
