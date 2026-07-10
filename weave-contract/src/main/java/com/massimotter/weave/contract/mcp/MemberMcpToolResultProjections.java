package com.massimotter.weave.contract.mcp;

import java.util.Map;

public final class MemberMcpToolResultProjections {
    private MemberMcpToolResultProjections() {}

    public static Map<String, Object> blocked(String reason) {
        return Map.of(
                "status", "blocked",
                "supportSafe", true,
                "reason", WeaveMcpTypes.text(reason, "reason"),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> filesSearch(Object items, String path, String query) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "dataPlane", "weave-webdav-facade",
                "webDavFacadePath", "/dav/files",
                "openApiDataPlaneUsed", false,
                "items", items,
                "queryRef", WeaveMcpTypes.text(query == null || query.isBlank() ? "*" : query, "filesQuery"),
                "canonicalRefs", Map.of("folder", "file:" + WeaveMcpTypes.text(path, "/")),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> filesReadMetadata(Object item, String fileRef) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "dataPlane", "weave-webdav-facade",
                "webDavFacadePath", "/dav/files",
                "openApiDataPlaneUsed", false,
                "item", item,
                "canonicalRefs", Map.of("file", WeaveMcpTypes.text(fileRef, "fileRef")),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> calendarSearchEvents(Object events, Object scope, String calendarScopeRef) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "dataPlane", "weave-caldav-facade",
                "openApiDataPlaneUsed", false,
                "events", events,
                "scope", scope,
                "canonicalRefs", Map.of("calendarScope", WeaveMcpTypes.text(calendarScopeRef, "calendarScopeRef")),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> calendarCreateEvent(Object event, String calendarEventRef, String calendarScopeRef) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "dataPlane", "weave-caldav-facade",
                "openApiDataPlaneUsed", false,
                "event", event,
                "canonicalRefs", Map.of(
                        "calendarEvent", WeaveMcpTypes.text(calendarEventRef, "calendarEventRef"),
                        "calendarScope", WeaveMcpTypes.text(calendarScopeRef, "calendarScopeRef")),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> chatSendMessage(
            String conversationId,
            String messageId,
            String deliveryState,
            Object sentAt) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "dataPlane", "weave-matrix-facade",
                "messageRef", "message:" + WeaveMcpTypes.text(messageId, "messageId"),
                "deliveryState", WeaveMcpTypes.text(deliveryState, "deliveryState"),
                "sentAt", sentAt,
                "canonicalRefs", Map.of(
                        "conversation", "conversation:" + WeaveMcpTypes.text(conversationId, "conversationId"),
                        "message", "message:" + messageId),
                "rawProviderPayload", "redacted");
    }
}
