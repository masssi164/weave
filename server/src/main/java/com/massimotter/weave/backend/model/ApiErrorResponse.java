package com.massimotter.weave.backend.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "Stable JSON error envelope returned by protected API endpoints.")
public record ApiErrorResponse(
        @Schema(description = "Stable machine-readable error code.", example = "unauthorized")
        String code,
        @Schema(description = "Operator-facing explanation of what failed.", example = "Bearer authentication is required to access this endpoint.")
        String message,
        @Schema(description = "Non-secret diagnostic details for this failure.")
        Map<String, Object> details,
        @Schema(description = "Correlation identifier for support and log lookup.", example = "8c2f4d7a-4d5f-4f88-92e8-4fcbb81dd527")
        String requestId) {
}
