package com.massimotter.weave.contract.mcp;

import java.util.List;
import java.util.Map;

public final class MemberMcpDtos {
    private MemberMcpDtos() {}

    public record EmptyRequest() {}
    public record RegistryToolsReadRequest(String domain, String capability, Boolean approvalRequiredOnly, Integer limit) {}
    public record FilesSearchRequest(String query, String spaceRef, int limit) {}
    public record FilesReadRequest(String fileRef) {}
    public record CalendarSearchEventsRequest(String from, String to, String query, String spaceRef, Integer limit) {}
    public record CalendarCreateEventRequest(String title, String startsAt, String endsAt, String calendarRef) {}
    public record BoardsSearchTasksRequest(String query, String boardRef, String assigneeRef, int limit) {}
    public record BoardsCommentRequest(String taskRef, String body) {}
    public record ChatSendMessageRequest(String threadRef, String body, String idempotencyKey) {}
    public record ToolResult(String auditRef, boolean supportSafe, Map<String, Object> data) {}
    public record ToolListResult(List<MemberMcpToolDefinition> tools) {}
}
