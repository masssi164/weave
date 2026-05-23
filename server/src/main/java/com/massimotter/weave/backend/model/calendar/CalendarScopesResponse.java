package com.massimotter.weave.backend.model.calendar;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Calendar scopes visible to the authenticated user.")
public record CalendarScopesResponse(
        @Schema(description = "Visible workspace, team, and channel calendar scopes.")
        List<CalendarScopeResponse> scopes) {
}
