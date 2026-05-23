package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Explicit calendar ownership/access model for product and external-client setup flows.")
public record CalendarAccessModelResponse(
        @Schema(description = "Stable access model identifier.", example = "workspace-calendar")
        String type,
        @Schema(description = "Product calendar scope served by /api/calendar/events.", example = "workspace")
        String productScope,
        @Schema(description = "Whether private per-user calendars are available through the product facade.", example = "false")
        boolean privateUserCalendarsAvailable,
        @Schema(description = "Support-safe reason private per-user calendars are unavailable or constrained.")
        String privateUserCalendarsReason,
        @Schema(description = "Credential model expected for external CalDAV clients.")
        String externalClientCredentialModel,
        @Schema(description = "Human-readable notes that keep the user/admin boundary honest.")
        List<String> notes) {
}
