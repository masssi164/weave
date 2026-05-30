package com.massimotter.weave.backend.weaver;

import java.util.List;
import java.util.Map;

public record WeaverToolInvocationRequest(
        String toolName,
        String userRef,
        List<String> grantedCapabilities,
        Map<String, Object> input,
        String approvalReceiptRef) {

    public WeaverToolInvocationRequest {
        grantedCapabilities = List.copyOf(grantedCapabilities == null ? List.of() : grantedCapabilities);
        input = Map.copyOf(input == null ? Map.of() : input);
    }
}
