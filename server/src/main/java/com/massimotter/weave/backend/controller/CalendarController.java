package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.model.calendar.CalendarClientSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventResponse;
import com.massimotter.weave.backend.model.calendar.CalendarEventsResponse;
import com.massimotter.weave.backend.model.calendar.CreateCalendarEventRequest;
import com.massimotter.weave.backend.model.calendar.UpdateCalendarEventRequest;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfile;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
@Tag(name = "Calendar", description = "Authenticated product calendar facade backed by Nextcloud CalDAV.")
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

    public CalendarController(CalendarFacadeService calendarFacadeService) {
        this.calendarFacadeService = calendarFacadeService;
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

    @GetMapping("/api/calendar/client-setup")
    @Operation(summary = "Describe native calendar client setup options")
    @ApiResponse(responseCode = "200", description = "Secret-free native calendar client setup metadata.",
            content = @Content(schema = @Schema(implementation = CalendarClientSetupResponse.class)))
    public CalendarClientSetupResponse clientSetup() {
        return calendarFacadeService.clientSetup();
    }


    @GetMapping("/api/calendar/access-policy")
    @Operation(summary = "Describe fail-closed private calendar access policy")
    public CalendarAccessPolicyResponse accessPolicy() {
        return calendarFacadeService.accessPolicy();
    }

    @GetMapping("/api/calendar/client-setup/credentials")
    @Operation(summary = "List revocable calendar setup credential references")
    public CalendarSetupCredentialListResponse setupCredentials() {
        return calendarFacadeService.setupCredentials();
    }

    @PostMapping("/api/calendar/client-setup/credentials")
    @Operation(summary = "Create a revocable calendar setup credential reference without returning secret material")
    public CalendarSetupCredentialResponse createSetupCredential(
            @Valid @RequestBody CalendarSetupCredentialRequest request) {
        return calendarFacadeService.createSetupCredential(request);
    }

    @DeleteMapping("/api/calendar/client-setup/credentials/{credentialId}")
    @Operation(summary = "Revoke a calendar setup credential reference")
    public CalendarSetupCredentialResponse revokeSetupCredential(@PathVariable @Size(max = 128) String credentialId) {
        return calendarFacadeService.revokeSetupCredential(credentialId);
    }

    @GetMapping(
            value = "/api/calendar/client-setup/apple.mobileconfig",
            produces = "application/x-apple-aspen-config")
    @Operation(summary = "Download a signed Apple Calendar setup profile")
    @ApiResponse(responseCode = "200", description = "Signed secret-free Apple Calendar .mobileconfig profile.",
            content = @Content(mediaType = "application/x-apple-aspen-config"))
    @ApiResponse(responseCode = "503", description = "Profile signing or revocable credential support is not configured yet.",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<byte[]> appleMobileConfigProfile() {
        AppleMobileConfigProfile profile = calendarFacadeService.appleMobileConfigProfile();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + profile.filename() + "\"")
                .body(profile.content());
    }

    @PostMapping("/api/calendar/events")
    @Operation(summary = "Create a calendar event")
    @ApiResponse(responseCode = "200", description = "Created calendar event.",
            content = @Content(schema = @Schema(implementation = CalendarEventResponse.class)))
    public CalendarEventResponse create(@Valid @RequestBody CreateCalendarEventRequest request) {
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
            @PathVariable @Size(max = 2048) String id,
            @Valid @RequestBody UpdateCalendarEventRequest request) {
        return calendarFacadeService.update(id, request);
    }

    @DeleteMapping("/api/calendar/events/{id}")
    @Operation(summary = "Delete a calendar event")
    public ResponseEntity<Void> delete(@PathVariable @Size(max = 2048) String id) {
        calendarFacadeService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
