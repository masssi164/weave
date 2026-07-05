package com.massimotter.weave.contract.mcp;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MemberMcpToolCatalog {
    public static final String SERVER_NAMESPACE = "weave-domain-tools";
    private static final List<MemberMcpToolDefinition> TOOLS = List.of(
            tool("registry.tools.read", "weave-runtime", MemberMcpToolMode.READ, "registry.tools.read", false, false),
            tool("files.search", MemberMcpDomainDefinition.FILES_DOCS.domain(), MemberMcpToolMode.READ, "files.read", false, true),
            tool("files.read", MemberMcpDomainDefinition.FILES_DOCS.domain(), MemberMcpToolMode.READ, "files.read", false, true),
            tool("calendar.search_events", MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(), MemberMcpToolMode.READ, "calendar.read", false, true),
            tool("calendar.create_event", MemberMcpDomainDefinition.CALENDAR_MEETINGS.domain(), MemberMcpToolMode.WRITE, "calendar.manage_events", true, true),
            tool("boards.search_tasks", MemberMcpDomainDefinition.BOARDS_TASKS.domain(), MemberMcpToolMode.READ, "boards.read", false, false),
            tool("boards.comment", MemberMcpDomainDefinition.BOARDS_TASKS.domain(), MemberMcpToolMode.WRITE, "boards.update_task", true, false),
            tool("chat.send_message", "chat", MemberMcpToolMode.EXTERNAL_SEND, "chat.send", true, false));

    private MemberMcpToolCatalog() {}
    public static List<MemberMcpToolDefinition> tools() { return TOOLS; }
    public static List<MemberMcpToolDefinition> serverExecutableTools() { return TOOLS.stream().filter(MemberMcpToolDefinition::serverExecutable).toList(); }
    public static Map<String, MemberMcpToolDefinition> byName() { Map<String, MemberMcpToolDefinition> map = new LinkedHashMap<>(); TOOLS.forEach(t -> map.put(t.name(), t)); return Map.copyOf(map); }
    public static WeaveMcpBridgeDtos.WeaveMcpToolCatalog bridgeCatalog() {
        return new WeaveMcpBridgeDtos.WeaveMcpToolCatalog(SERVER_NAMESPACE, MemberMcpDomainDefinition.CONTRACT_VERSION, TOOLS.stream().map(MemberMcpToolDefinition::asBridgeDefinition).toList());
    }

    private static MemberMcpToolDefinition tool(String name, String domain, MemberMcpToolMode mode, String capability, boolean approval, boolean serverExecutable) {
        return new MemberMcpToolDefinition(name, "v1", domain, mode, capability, approval, serverExecutable, schema(name), "Delegates to weave-server; provider adapters and policy remain server-side.");
    }

    private static Map<String, Object> schema(String toolName) {
        return switch (toolName) {
            case "registry.tools.read" -> objectSchema(toolName, Map.of(
                    "domain", stringProperty("Canonical domain filter such as files-docs."),
                    "capability", stringProperty("Canonical capability filter such as files.read."),
                    "approvalRequiredOnly", booleanProperty("Only include approval-required tools."),
                    "limit", integerProperty("Maximum number of tools to return.", 1)));
            case "files.search" -> objectSchema(toolName, Map.of(
                    "query", stringProperty("Search query."),
                    "path", stringProperty("Optional Weave Files product path to search from."),
                    "spaceRef", stringProperty("Optional canonical space reference."),
                    "limit", integerProperty("Maximum number of results.", 1)));
            case "files.read" -> objectSchema(toolName, List.of("fileRef"), Map.of(
                    "fileRef", stringProperty("Canonical Weave file reference, for example file:/Team/readme.md.")));
            case "calendar.search_events" -> objectSchema(toolName, Map.of(
                    "from", stringProperty("Inclusive ISO-8601 start boundary."),
                    "to", stringProperty("Exclusive ISO-8601 end boundary."),
                    "query", stringProperty("Optional full-text filter."),
                    "spaceRef", stringProperty("Optional canonical space reference."),
                    "limit", integerProperty("Maximum number of events to return.", 1)));
            case "calendar.create_event" -> objectSchema(toolName, List.of("title", "startsAt"), Map.of(
                    "title", stringProperty("Support-safe event title."),
                    "startsAt", stringProperty("ISO-8601 event start timestamp."),
                    "endsAt", stringProperty("ISO-8601 event end timestamp."),
                    "calendarRef", stringProperty("Optional canonical calendar reference.")));
            case "boards.search_tasks" -> objectSchema(toolName, Map.of(
                    "query", stringProperty("Optional task search query."),
                    "boardRef", stringProperty("Optional canonical board reference."),
                    "assigneeRef", stringProperty("Optional canonical assignee reference."),
                    "limit", integerProperty("Maximum number of tasks to return.", 1)));
            case "boards.comment" -> objectSchema(toolName, List.of("taskRef", "body"), Map.of(
                    "taskRef", stringProperty("Canonical task reference."),
                    "body", stringProperty("Support-safe comment body.")));
            case "chat.send_message" -> objectSchema(toolName, List.of("threadRef", "body"), Map.of(
                    "threadRef", stringProperty("Canonical chat thread reference."),
                    "body", stringProperty("Message body."),
                    "idempotencyKey", stringProperty("Optional client-supplied idempotency key.")));
            default -> objectSchema(toolName, Map.of());
        };
    }

    private static Map<String, Object> objectSchema(String title, Map<String, Object> properties) {
        return objectSchema(title, List.of(), properties);
    }

    private static Map<String, Object> objectSchema(String title, List<String> required, Map<String, Object> properties) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("title", title + ".input");
        schema.put("additionalProperties", false);
        schema.put("properties", Map.copyOf(properties));
        if (!required.isEmpty()) schema.put("required", List.copyOf(required));
        return Map.copyOf(schema);
    }

    private static Map<String, Object> stringProperty(String description) {
        return Map.of("type", "string", "description", description);
    }

    private static Map<String, Object> booleanProperty(String description) {
        return Map.of("type", "boolean", "description", description);
    }

    private static Map<String, Object> integerProperty(String description, int minimum) {
        return Map.of("type", "integer", "description", description, "minimum", minimum);
    }
}
