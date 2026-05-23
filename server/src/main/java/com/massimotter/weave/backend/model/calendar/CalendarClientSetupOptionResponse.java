package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "A platform-specific external calendar setup option.")
public record CalendarClientSetupOptionResponse(
        @Schema(description = "Stable platform identifier.", example = "apple")
        String platform,
        @Schema(description = "Setup mechanism.", example = "mobileconfig")
        String method,
        @Schema(description = "Whether this setup method is currently safe for feature-gated use.", example = "false")
        boolean available,
        @Schema(description = "Optional action URL for supported external clients. Contains no credential.")
        String actionUrl,
        @Schema(description = "Support-safe reason when the option is not available yet.")
        String unavailableReason,
        @Schema(description = "Human-readable setup notes for the authenticated user.")
        List<String> notes) {
}
