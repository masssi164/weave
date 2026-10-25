package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Stable JSON error envelope returned by protected API endpoints.")
public record ApiErrorResponse(
        @Schema(description = "Stable machine-readable error code.", example = "unauthorized", requiredMode = Schema.RequiredMode.REQUIRED)
        String code,
        @Schema(description = "Support-safe explanation of what failed.", example = "Bearer authentication is required to access this endpoint.", requiredMode = Schema.RequiredMode.REQUIRED)
        String message,
        @Schema(description = "Non-secret diagnostic details for this failure.", requiredMode = Schema.RequiredMode.REQUIRED)
        Map<String, Object> details,
        @Schema(description = "Correlation identifier for support and log lookup.", example = "8c2f4d7a-4d5f-4f88-92e8-4fcbb81dd527", requiredMode = Schema.RequiredMode.REQUIRED)
        String requestId,
        @Schema(description = "Stable reference members can share with support without provider payloads.", example = "support:8c2f4d7a-4d5f-4f88-92e8-4fcbb81dd527", requiredMode = Schema.RequiredMode.REQUIRED)
        String supportRef,
        @Schema(description = "Optional member-facing impact state or recovery hint owned by the backend facade.", example = "unavailable")
        String memberImpact) {
}
