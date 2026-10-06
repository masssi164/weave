package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Calendar", description = "Authenticated product calendar facade backed by Weave-owned calendar projections.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or the required effective Calendar capability.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Calendar storage adapter is not configured or unavailable.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CalendarController {

    private final CalendarFacadeService calendarFacadeService;
    public CalendarController(CalendarFacadeService calendarFacadeService) {
        this.calendarFacadeService = calendarFacadeService;
    }

    @GetMapping("/api/calendar/scopes")
    @Operation(operationId = "scopes", summary = "List visible workspace, team, and channel calendar scopes")
    @ApiResponse(responseCode = "200", description = "Visible calendar scopes.",
            content = @Content(schema = @Schema(implementation = CalendarScopesResponse.class)))
    public CalendarScopesResponse scopes() {
        return calendarFacadeService.scopes();
    }

    @GetMapping("/api/calendar/access-policy")
    @Operation(operationId = "accessPolicy", summary = "Describe fail-closed private calendar access policy")
    public CalendarAccessPolicyResponse accessPolicy() {
        return calendarFacadeService.accessPolicy();
    }

}
