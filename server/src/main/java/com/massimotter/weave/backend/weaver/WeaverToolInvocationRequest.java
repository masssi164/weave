package com.massimotter.weave.backend.weaver;

import java.util.List;
import java.util.Map;

public record WeaverToolInvocationRequest(
        String toolName,
        String userRef,
        String runtimeProfileHash,
        List<String> grantedCapabilities,
        Map<String, Object> input,
        String approvalReceiptRef) {

    public WeaverToolInvocationRequest {
        if (runtimeProfileHash == null || runtimeProfileHash.isBlank()) {
            throw new IllegalArgumentException("runtimeProfileHash is required for Weaver tool audit correlation");
        }
        grantedCapabilities = List.copyOf(grantedCapabilities == null ? List.of() : grantedCapabilities);
        input = Map.copyOf(input == null ? Map.of() : input);
    }
}
