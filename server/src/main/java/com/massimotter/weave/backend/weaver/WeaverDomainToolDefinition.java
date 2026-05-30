package com.massimotter.weave.backend.weaver;

import java.util.List;
import java.util.Map;

public record WeaverDomainToolDefinition(
        String name,
        String version,
        String domain,
        WeaverToolMode mode,
        String requiredCapability,
        WeaverApprovalRequirement approvalRequirement,
        Map<String, Object> inputSchema,
        List<String> resultRedactionRules,
        String supportSafeDescription) {

    public WeaverDomainToolDefinition {
        inputSchema = Map.copyOf(inputSchema == null ? Map.of() : inputSchema);
        resultRedactionRules = List.copyOf(resultRedactionRules == null ? List.of() : resultRedactionRules);
    }

    public boolean writeLike() {
        return mode == WeaverToolMode.WRITE || mode == WeaverToolMode.EXTERNAL_SEND || mode == WeaverToolMode.PROVIDER_SWITCH;
    }
}
