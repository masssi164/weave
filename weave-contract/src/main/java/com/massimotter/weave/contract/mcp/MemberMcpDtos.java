package com.massimotter.weave.contract.mcp;

import java.util.List;
import java.util.Map;

public final class MemberMcpDtos {
    private MemberMcpDtos() {}

    public record EmptyRequest() {}
    public record FilesSearchRequest(String query, String spaceRef, int limit) {}
    public record FilesReadRequest(String fileRef) {}
    public record CalendarSearchEventsRequest(String from, String to, String query, String spaceRef) {}
    public record CalendarCreateEventRequest(String title, String startsAt, String endsAt, String calendarRef, String approvalReceiptRef) {}
    public record BoardsSearchTasksRequest(String query, String boardRef, int limit) {}
    public record BoardsCommentRequest(String taskRef, String body, String approvalReceiptRef) {}
    public record ToolResult(String auditRef, boolean supportSafe, Map<String, Object> data) {}
    public record ToolListResult(List<MemberMcpToolDefinition> tools) {}
}
