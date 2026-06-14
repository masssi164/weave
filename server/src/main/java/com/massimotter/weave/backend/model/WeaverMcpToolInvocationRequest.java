package com.massimotter.weave.backend.model;

import com.massimotter.weave.backend.weaver.WeaverApprovalReceipt;
import java.util.Map;

public record WeaverMcpToolInvocationRequest(
        String runtimeProfileHash,
        Map<String, Object> input,
        WeaverApprovalReceipt approvalReceipt) {

    public WeaverMcpToolInvocationRequest {
        runtimeProfileHash = runtimeProfileHash == null ? "" : runtimeProfileHash.trim();
        input = Map.copyOf(input == null ? Map.of() : input);
    }
}
