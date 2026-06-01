package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "One support-safe item projected into a governed Weaver RuntimeProfile preview.")
public record WeaverRuntimeProjectionItemResponse(
        String id,
        String category,
        String label,
        String state,
        String memberImpact,
        String policyImpact,
        String readinessSummary,
        List<String> receiptRefs,
        boolean discoverableToRuntime,
        boolean approvalRequired) {
    public WeaverRuntimeProjectionItemResponse {
        receiptRefs = receiptRefs == null ? List.of() : List.copyOf(receiptRefs);
    }
}
