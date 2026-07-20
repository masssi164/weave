package com.massimotter.weave.mcp;

import io.modelcontextprotocol.common.McpTransportContext;
import org.springframework.http.HttpHeaders;

public record RuntimeHeaders(String runtimeProfile) {

    static final String RUNTIME_PROFILE = "weave.runtime-profile";

    static RuntimeHeaders from(McpTransportContext context) {
        return new RuntimeHeaders(value(context, RUNTIME_PROFILE));
    }

    boolean valid() {
        return runtimeProfile != null && !runtimeProfile.isBlank();
    }

    void copyTo(HttpHeaders headers) {
        if (runtimeProfile != null && !runtimeProfile.isBlank()) headers.set("X-Weave-Runtime-Profile", runtimeProfile);
    }

    private static String value(McpTransportContext context, String key) {
        Object value = context == null ? null : context.get(key);
        return value instanceof String text && !text.isBlank() ? text : null;
    }
}
