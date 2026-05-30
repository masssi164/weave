package com.massimotter.weave.backend.weaver;

import java.util.Map;

public record WeaverToolInvocationResult(
        String toolName,
        String status,
        boolean approvalRequired,
        boolean audited,
        Map<String, Object> redactedResult,
        String supportSafeMessage) {

    public WeaverToolInvocationResult {
        redactedResult = Map.copyOf(redactedResult == null ? Map.of() : redactedResult);
    }
}
