package com.massimotter.weave.mcp;

import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeDiscoveryResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.BridgeInvocationResponse;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ApprovalEvidence;
import com.massimotter.weave.contract.mcp.WeaveMcpBridgeDtos.ToolInvocationStatus;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.mcp.annotation.McpArg;
import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpPrompt;
import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.context.MetaProvider;
import org.springframework.ai.mcp.annotation.context.McpSyncRequestContext;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class CanonicalMcpFeatures {

    static final String APPROVED_TOOLS_RESOURCE = "weave://runtime/approved-tools";
    static final String WORKSPACE_PROMPT = "weave.workspace.plan";

    private final WeaveServerClient client;
    private final JsonMapper jsonMapper;
    private final McpToolApprovalService approvalService;

    public CanonicalMcpFeatures(
            WeaveServerClient client,
            JsonMapper jsonMapper,
            McpToolApprovalService approvalService) {
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.approvalService = approvalService;
    }

    @McpTool(
            name = "files.search",
            title = "Search Weave files",
            description = "Search canonical Weave file metadata through the Files facade.",
            annotations = @McpTool.McpAnnotations(
                    title = "Search Weave files",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false),
            metaProvider = FilesSearchMetadata.class)
    public McpSchema.CallToolResult searchFiles(
            McpTransportContext context,
            McpMeta metadata,
            @McpToolParam(description = "Search query.", required = false) String query,
            @McpToolParam(description = "Optional Weave Files product path to search from.", required = false)
                    String path,
            @McpToolParam(description = "Maximum number of results.", required = false) Integer limit) {
        return invoke("files.search", arguments("query", query, "path", path, "limit", limit), context, metadata);
    }

    @McpTool(
            name = "files.read",
            title = "Read Weave file metadata",
            description = "Read support-safe metadata for a canonical Weave file reference.",
            annotations = @McpTool.McpAnnotations(
                    title = "Read Weave file metadata",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false),
            metaProvider = FilesReadMetadata.class)
    public McpSchema.CallToolResult readFile(
            McpTransportContext context,
            McpMeta metadata,
            @McpToolParam(
                            description = "Canonical Weave file reference, for example file:/Team/readme.md.",
                            required = true)
                    String fileRef) {
        return invoke("files.read", arguments("fileRef", fileRef), context, metadata);
    }

    @McpTool(
            name = "calendar.search_events",
            title = "Search Weave calendar events",
            description = "Search canonical Weave calendar events through the Calendar facade.",
            annotations = @McpTool.McpAnnotations(
                    title = "Search Weave calendar events",
                    readOnlyHint = true,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false),
            metaProvider = CalendarSearchMetadata.class)
    public McpSchema.CallToolResult searchCalendarEvents(
            McpTransportContext context,
            McpMeta metadata,
            @McpToolParam(description = "Inclusive ISO-8601 start boundary.", required = false) String from,
            @McpToolParam(description = "Exclusive ISO-8601 end boundary.", required = false) String to,
            @McpToolParam(description = "Optional full-text filter.", required = false) String query,
            @McpToolParam(
                            description = "Canonical calendar ref: calendar:workspace, calendar:team:<team>, or calendar:channel:<team>:<channel>.",
                            required = false)
                    String calendarRef,
            @McpToolParam(description = "Maximum number of events to return.", required = false) Integer limit) {
        return invoke(
                "calendar.search_events",
                arguments("from", from, "to", to, "query", query, "calendarRef", calendarRef, "limit", limit),
                context,
                metadata);
    }

    @McpTool(
            name = "calendar.create_event",
            title = "Create a Weave calendar event",
            description = "Create an event in an explicitly scoped canonical Weave calendar after approval.",
            annotations = @McpTool.McpAnnotations(
                    title = "Create a Weave calendar event",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = false,
                    openWorldHint = false),
            metaProvider = CalendarCreateMetadata.class)
    public McpSchema.CallToolResult createCalendarEvent(
            McpTransportContext context,
            McpSyncRequestContext requestContext,
            McpMeta metadata,
            @McpToolParam(description = "Support-safe event title.", required = true) String title,
            @McpToolParam(description = "Optional plain-text event description.", required = false)
                    String description,
            @McpToolParam(description = "ISO-8601 event start timestamp.", required = true) String startsAt,
            @McpToolParam(description = "ISO-8601 event end timestamp.", required = false) String endsAt,
            @McpToolParam(description = "IANA timezone for display and editing.", required = false) String timezone,
            @McpToolParam(description = "Optional event location.", required = false) String location,
            @McpToolParam(description = "Whether the event is all-day.", required = false) Boolean allDay,
            @McpToolParam(
                            description = "Canonical calendar ref: calendar:workspace, calendar:team:<team>, or calendar:channel:<team>:<channel>.",
                            required = true)
                    String calendarRef) {
        Map<String, Object> toolArguments = arguments(
                        "title", title,
                        "description", description,
                        "startsAt", startsAt,
                        "endsAt", endsAt,
                        "timezone", timezone,
                        "location", location,
                        "allDay", allDay,
                        "calendarRef", calendarRef);
        ApprovalEvidence approval = approvalService.requireApproval(
                requestContext,
                "calendar.create_event",
                "Create calendar event",
                "Create one event in " + calendarRef + ".",
                toolArguments);
        return invoke(
                "calendar.create_event",
                toolArguments,
                context,
                metadata,
                approval);
    }

    @McpTool(
            name = "chat.send_message",
            title = "Send a Weave chat message",
            description = "Send one idempotent message to a canonical Weave chat thread after approval.",
            annotations = @McpTool.McpAnnotations(
                    title = "Send a Weave chat message",
                    readOnlyHint = false,
                    destructiveHint = false,
                    idempotentHint = true,
                    openWorldHint = false),
            metaProvider = ChatSendMetadata.class)
    public McpSchema.CallToolResult sendChatMessage(
            McpTransportContext context,
            McpSyncRequestContext requestContext,
            McpMeta metadata,
            @McpToolParam(
                            description = "Canonical Weave chat reference, for example thread:channel-general.",
                            required = true)
                    String threadRef,
            @McpToolParam(description = "Message body.", required = true) String body,
            @McpToolParam(description = "Caller-stable idempotency key for safe retries.", required = true)
                    String idempotencyKey) {
        Map<String, Object> toolArguments = arguments(
                "threadRef", threadRef,
                "body", body,
                "idempotencyKey", idempotencyKey);
        ApprovalEvidence approval = approvalService.requireApproval(
                requestContext,
                "chat.send_message",
                "Send chat message",
                "Send one message to " + threadRef + ".",
                toolArguments);
        return invoke(
                "chat.send_message",
                toolArguments,
                context,
                metadata,
                approval);
    }

    @McpResource(
            uri = APPROVED_TOOLS_RESOURCE,
            name = "weave.approved-tools",
            title = "Approved Weave domain tools",
            description = "Runtime-profile-approved Weave capabilities with support-safe metadata.",
            mimeType = "application/json",
            metaProvider = CanonicalMetadata.class)
    public McpSchema.ReadResourceResult approvedTools(McpTransportContext context) {
        BridgeDiscoveryResponse discovery = discover(context);
        McpSchema.TextResourceContents contents = McpSchema.TextResourceContents.builder(
                        APPROVED_TOOLS_RESOURCE,
                        writeJson(approvedCatalog(discovery)))
                .mimeType("application/json")
                .build();
        return McpSchema.ReadResourceResult.builder(List.of(contents)).build();
    }

    @McpPrompt(
            name = WORKSPACE_PROMPT,
            title = "Plan work with approved Weave capabilities",
            description = "Prepare bounded workspace work using only the caller's approved canonical tools.",
            metaProvider = CanonicalMetadata.class)
    public McpSchema.GetPromptResult workspacePlan(
            McpTransportContext context,
            @McpArg(
                            name = "objective",
                            description = "Work objective without provider-specific instructions.",
                            required = true)
                    String objective) {
        BridgeDiscoveryResponse discovery = discover(context);
        String approved = discovery.catalog().tools().stream()
                .map(tool -> tool.name())
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
        String instruction = "Objective: " + text(objective, "Plan the requested workspace work.")
                + "\nUse only these approved Weave domain tools: " + approved
                + ". Do not request provider credentials, raw downstream payloads, or direct provider APIs."
                + " Treat approval-required actions as blocked until standard MCP form elicitation returns an approved, argument-bound receipt.";
        return new McpSchema.GetPromptResult(
                "Bounded Weave workspace plan",
                List.of(new McpSchema.PromptMessage(
                        McpSchema.Role.USER,
                        McpSchema.TextContent.builder(instruction).build())),
                Map.of("supportSafe", true, "approvedToolCount", discovery.catalog().tools().size()));
    }

    private McpSchema.CallToolResult invoke(
            String toolName,
            Map<String, Object> arguments,
            McpTransportContext context,
            McpMeta metadata) {
        return invoke(toolName, arguments, context, metadata, null);
    }

    private McpSchema.CallToolResult invoke(
            String toolName,
            Map<String, Object> arguments,
            McpTransportContext context,
            McpMeta metadata,
            ApprovalEvidence approvalEvidence) {
        try {
            RuntimeHeaders headers = RuntimeHeaders.from(context);
            BridgeInvocationResponse response = client.invoke(toolName, arguments, headers, approvalEvidence);
            McpSchema.CallToolResult.Builder result = McpSchema.CallToolResult.builder()
                    .isError(response.status() != ToolInvocationStatus.SUCCESS)
                    .structuredContent(response.structuredContent())
                    .meta(Map.of(
                            "auditRef", response.auditRef(),
                            "status", response.status().name().toLowerCase(),
                            "supportSafe", response.supportSafe()));
            response.content().forEach(block -> result.addTextContent(block.text()));
            return result.build();
        } catch (McpBoundaryException exception) {
            return denied(exception.getMessage());
        } catch (RuntimeException exception) {
            return denied("mcp-canonical-boundary-unavailable");
        }
    }

    private BridgeDiscoveryResponse discover(McpTransportContext context) {
        RuntimeHeaders headers = RuntimeHeaders.from(context);
        if (!headers.valid()) {
            throw new McpBoundaryException("mcp-runtime-context-missing");
        }
        return client.discover(headers.runtimeProfile(), headers);
    }

    private McpSchema.CallToolResult denied(String reason) {
        return McpSchema.CallToolResult.builder()
                .addTextContent("Weave denied the MCP request at the canonical policy boundary.")
                .isError(true)
                .structuredContent(Map.of(
                        "status", "denied",
                        "reason", text(reason, "mcp-request-denied"),
                        "supportSafe", true))
                .meta(Map.of("supportSafe", true))
                .build();
    }

    private Map<String, Object> approvedCatalog(BridgeDiscoveryResponse discovery) {
        List<Map<String, Object>> tools = discovery.catalog().tools().stream().map(tool -> Map.<String, Object>of(
                "name", tool.name(),
                "domain", tool.domain(),
                "requiredCapability", tool.requiredCapability(),
                "approvalRequired", tool.approvalRequired(),
                "description", tool.description())).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("serverNamespace", discovery.catalog().serverNamespace());
        result.put("contractVersion", discovery.catalog().contractVersion());
        result.put("tools", tools);
        result.put("supportSafe", true);
        result.put("providerInternalsExposed", false);
        return Map.copyOf(result);
    }

    private String writeJson(Object value) {
        try {
            return jsonMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new McpBoundaryException("mcp-resource-serialization-failed");
        }
    }

    private Map<String, Object> arguments(Object... values) {
        Map<String, Object> arguments = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            Object value = values[index + 1];
            if (value != null) {
                arguments.put((String) values[index], value);
            }
        }
        return Map.copyOf(arguments);
    }

    private Map<String, Object> metadata(McpMeta metadata) {
        return metadata == null || metadata.meta() == null ? Map.of() : metadata.meta();
    }

    private String text(Object value, String fallback) {
        return value instanceof String candidate && !candidate.isBlank() ? candidate.strip() : fallback;
    }

    private static Map<String, Object> declarationMetadata(
            String domain,
            String capability,
            boolean approvalRequired) {
        return Map.of(
                "domain", domain,
                "requiredCapability", capability,
                "approvalRequired", approvalRequired,
                "supportSafe", true,
                "canonical", true);
    }

    public static final class FilesSearchMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return declarationMetadata("files-docs", "files.read", false);
        }
    }

    public static final class FilesReadMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return declarationMetadata("files-docs", "files.read", false);
        }
    }

    public static final class CalendarSearchMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return declarationMetadata("calendar-meetings", "calendar.read", false);
        }
    }

    public static final class CalendarCreateMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return declarationMetadata("calendar-meetings", "calendar.manage_events", true);
        }
    }

    public static final class ChatSendMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return declarationMetadata("chat", "chat.send", true);
        }
    }

    public static final class CanonicalMetadata implements MetaProvider {
        @Override
        public Map<String, Object> getMeta() {
            return Map.of("supportSafe", true, "canonical", true);
        }
    }
}
