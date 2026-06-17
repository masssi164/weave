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

    public static Map<String, Object> calendarSearchEvents(Object events, Object scope, String calendarScopeRef) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "events", events,
                "scope", scope,
                "canonicalRefs", Map.of("calendarScope", WeaveMcpTypes.text(calendarScopeRef, "calendarScopeRef")),
                "rawProviderPayload", "redacted");
    }

    public static Map<String, Object> calendarCreateEvent(Object event, String calendarEventRef, String calendarScopeRef) {
        return Map.of(
                "status", "ok",
                "supportSafe", true,
                "event", event,
                "canonicalRefs", Map.of(
                        "calendarEvent", WeaveMcpTypes.text(calendarEventRef, "calendarEventRef"),
                        "calendarScope", WeaveMcpTypes.text(calendarScopeRef, "calendarScopeRef")),
                "rawProviderPayload", "redacted");
    }
}
