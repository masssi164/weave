package com.massimotter.weave.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.http.HttpHeaders;

public record RuntimeHeaders(
        String authorization,
        String orgId,
        String userRef,
        String runtimeProfile) {

    static final String AUTHORIZATION = "authorization";
    static final String ORG_ID = "weave.org-id";
    static final String USER_REF = "weave.user-ref";
    static final String RUNTIME_PROFILE = "weave.runtime-profile";
    static RuntimeHeaders from(McpTransportContext context) {
        return new RuntimeHeaders(
                value(context, AUTHORIZATION),
                value(context, ORG_ID),
                value(context, USER_REF),
                value(context, RUNTIME_PROFILE));
    }

    boolean valid() {
        return authorization != null
                && authorization.startsWith("Bearer ")
                && runtimeProfile != null
                && !runtimeProfile.isBlank();
    }

    void copyTo(HttpHeaders headers) {
        if (authorization != null && !authorization.isBlank()) headers.set(HttpHeaders.AUTHORIZATION, authorization);
        if (orgId != null && !orgId.isBlank()) headers.set("X-Weave-Org-Id", orgId);
        if (userRef != null && !userRef.isBlank()) headers.set("X-Weave-User-Ref", userRef);
        if (runtimeProfile != null && !runtimeProfile.isBlank()) headers.set("X-Weave-Runtime-Profile", runtimeProfile);
    }

    private static String value(McpTransportContext context, String key) {
        Object value = context == null ? null : context.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
