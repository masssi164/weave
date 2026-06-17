package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Calendar", description = "Authenticated product calendar-meetings facade backed by Weave canonical domain contracts.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Downstream calendar adapter is not configured or unavailable.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CalendarController {

    private final CalendarFacadeService calendarFacadeService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public CalendarController(CalendarFacadeService calendarFacadeService,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.calendarFacadeService = calendarFacadeService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/calendar/scopes")
    @Operation(summary = "List visible workspace, team, and channel calendar scopes")
    @ApiResponse(responseCode = "200", description = "Visible calendar scopes.",
            content = @Content(schema = @Schema(implementation = CalendarScopesResponse.class)))
    public CalendarScopesResponse scopes() {
        return calendarFacadeService.scopes();
    }

    @GetMapping("/api/calendar/events")
    @Operation(summary = "List calendar events")
    @ApiResponse(responseCode = "200", description = "Calendar event listing.",
            content = @Content(schema = @Schema(implementation = CalendarEventsResponse.class)))
    public CalendarEventsResponse list(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            OffsetDateTime to,
            @RequestParam(required = false)
            @Size(max = 64)
            String scopeType,
            @RequestParam(required = false)
            @Size(max = 128)
            String teamId,
            @RequestParam(required = false)
            @Size(max = 128)
            String channelId) {
        return calendarFacadeService.list(from, to, scopeType, teamId, channelId);
    }

    @GetMapping("/api/calendar/access-policy")
    @Operation(summary = "Describe fail-closed private calendar access policy")
    public CalendarAccessPolicyResponse accessPolicy() {
        return calendarFacadeService.accessPolicy();
    }


    @PostMapping("/api/calendar/events")
    @Operation(summary = "Create a calendar event")
    @ApiResponse(responseCode = "200", description = "Created calendar event.",
            content = @Content(schema = @Schema(implementation = CalendarEventResponse.class)))
    public CalendarEventResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateCalendarEventRequest request) {
        workspaceCapabilityService.requireCapability(jwt, "calendar.manage_events", "calendar", "create-event");
        return calendarFacadeService.create(request);
    }

    @GetMapping("/api/calendar/events/{id}")
    @Operation(summary = "Read a calendar event")
    @ApiResponse(responseCode = "200", description = "Calendar event.",
            content = @Content(schema = @Schema(implementation = CalendarEventResponse.class)))
    public CalendarEventResponse read(@PathVariable @Size(max = 2048) String id) {
        return calendarFacadeService.read(id);
    }

    @PatchMapping("/api/calendar/events/{id}")
    @Operation(summary = "Update a calendar event")
    @ApiResponse(responseCode = "200", description = "Updated calendar event.",
            content = @Content(schema = @Schema(implementation = CalendarEventResponse.class)))
    public CalendarEventResponse update(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 2048) String id,
            @Valid @RequestBody UpdateCalendarEventRequest request) {
        workspaceCapabilityService.requireCapability(jwt, "calendar.manage_events", "calendar", "update-event");
        return calendarFacadeService.update(id, request);
    }

    @DeleteMapping("/api/calendar/events/{id}")
    @Operation(summary = "Delete a calendar event")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 2048) String id) {
        workspaceCapabilityService.requireCapability(jwt, "calendar.manage_events", "calendar", "delete-event");
        calendarFacadeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
