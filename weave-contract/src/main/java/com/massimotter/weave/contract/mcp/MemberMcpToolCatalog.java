package com.massimotter.weave.contract.mcp;

import static com.massimotter.weave.contract.mcp.MemberMcpDtos.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemberMcpToolCatalog {
    public static final String SERVER_NAMESPACE = "weave-mcp";
    private static final List<MemberMcpToolDefinition> TOOLS = List.of(
            tool("registry.tools.read", "weave-runtime", MemberMcpToolMode.READ, "registry.tools.read", false),
            tool("files.search", MemberMcpDomainDefinition.FILES_DOCS.domain(), MemberMcpToolMode.READ, "files.read", false),
            tool("files.read", MemberMcpDomainDefinition.FILES_DOCS.domain(), MemberMcpToolMode.READ, "files.read", false),
            tool("calendar.search_events", MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(), MemberMcpToolMode.READ, "calendar.read", false),
            tool("calendar.create_event", MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(), MemberMcpToolMode.WRITE, "calendar.manage_events", true),
            tool("boards.search_tasks", MemberMcpDomainDefinition.BOARDS_TASKS.domain(), MemberMcpToolMode.READ, "boards.read", false),
            tool("boards.comment", MemberMcpDomainDefinition.BOARDS_TASKS.domain(), MemberMcpToolMode.WRITE, "boards.update_task", true));

    private MemberMcpToolCatalog() {}
    public static List<MemberMcpToolDefinition> tools() { return TOOLS; }
    public static Map<String, MemberMcpToolDefinition> byName() { Map<String, MemberMcpToolDefinition> map = new LinkedHashMap<>(); TOOLS.forEach(t -> map.put(t.name(), t)); return Map.copyOf(map); }

    private static MemberMcpToolDefinition tool(String name, String domain, MemberMcpToolMode mode, String capability, boolean approval) {
        return new MemberMcpToolDefinition(name, "v1", domain, mode, capability, approval, schema(name), "Delegates to weave-server; provider adapters and policy remain server-side.");
    }

    private static Map<String, Object> schema(String toolName) {
        return Map.of("type", "object", "title", toolName + ".input", "additionalProperties", false);
    }
}
