package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.config.ApiErrorResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.massimotter.weave.backend.model.calendar.CalendarUserModels.*;
import com.massimotter.weave.backend.service.calendar.CalendarUserApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping(value = "/api/calendar/calendars", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Calendar User", description = "Calendar product operations under current member and Space authorization.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
    @ApiResponse(responseCode = "400", description = "Invalid or unsupported Calendar input.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "401", description = "A valid User session is required.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "403", description = "Organization, capability or Space access denied.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "404", description = "Calendar or event is absent or not visible.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(responseCode = "503", description = "Current provider, mapping, audit or lossless profile support unavailable.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CalendarUserController {
    private final CalendarUserApiService calendar;
    private final ApiErrorResponseWriter errors;
    public CalendarUserController(CalendarUserApiService calendar, ApiErrorResponseWriter errors) { this.calendar = calendar; this.errors = errors; }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public void invalidQueryValue(HttpServletRequest request, HttpServletResponse response) throws IOException {
        errors.write(request, response, HttpStatus.BAD_REQUEST, "calendar-invalid-query", "Calendar query parameters are invalid.");
    }

    @GetMapping
    @Operation(operationId = "listUserCalendars", summary = "Discover authorized workspace, team and channel calendars")
    @ApiResponse(responseCode = "200", description = "Visible calendars and effective actions.")
    public Calendars calendars(@AuthenticationPrincipal Jwt jwt) { return calendar.calendars(jwt); }

    @GetMapping("/{calendarId}/events")
    @Operation(operationId = "queryCalendarAgenda", summary = "Query a bounded Calendar agenda with an explicit evaluation timezone")
    @ApiResponse(responseCode = "200", description = "Exact event content and occurrence projections for the requested window.")
    @ApiResponse(responseCode = "422", description = "Event or occurrence result limit exceeded; request a smaller window.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public Agenda agenda(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId,
            @RequestParam Instant from, @RequestParam Instant to,
            @RequestParam @Parameter(description = "IANA timezone for DATE/FLOATING occurrence evaluation; stored intent is unchanged.") String evaluationTimeZone) {
        return calendar.agenda(jwt, calendarId, from, to, evaluationTimeZone);
    }

    @GetMapping("/{calendarId}/events/previews/{handle}")
    @Operation(operationId = "readCalendarEventPreview", summary = "Read a transient Calendar event preview under current authorization")
    @ApiResponse(responseCode = "200", description = "Current provider-backed event preview without a stable Event identity.")
    @ApiResponse(responseCode = "412", description = "The provider event or binding changed after the preview was issued.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<EventPreview> readPreview(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId,
            @PathVariable String handle) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(calendar.readPreview(jwt, calendarId, handle));
    }

    @PostMapping("/{calendarId}/events/previews/{handle}/materialization")
    @Operation(operationId = "materializeCalendarEventPreview", summary = "Explicitly create or reuse a stable reference to a provider event")
    @ApiResponse(responseCode = "200", description = "Idempotently materialized stable event.", headers = @Header(name = "ETag", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "412", description = "The provider event or binding changed after the preview was issued.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Event> materializePreview(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId,
            @PathVariable String handle) {
        return response(calendar.materializePreview(jwt, calendarId, handle), HttpStatus.OK);
    }

    @GetMapping("/{calendarId}/events/{eventId}")
    @Operation(operationId = "getCalendarEvent", summary = "Read a stable Weave Calendar event")
    @ApiResponse(responseCode = "200", description = "Exact current event content.", headers = @Header(name = "ETag", schema = @Schema(type = "string")))
    public ResponseEntity<Event> read(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId, @PathVariable String eventId) {
        return response(calendar.read(jwt, calendarId, eventId), HttpStatus.OK);
    }

    @PostMapping(value = "/{calendarId}/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "createCalendarEvent", summary = "Create an event with a stable retry identity")
    @ApiResponse(responseCode = "201", description = "Created event, or unchanged successful replay.", headers = @Header(name = "ETag", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "409", description = "The create identity is already bound to another payload.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Event> create(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId,
            @RequestHeader(value = "Idempotency-Key", required = false)
            @Parameter(required = true, schema = @Schema(type = "string", minLength = 16, maxLength = 128, pattern = "[A-Za-z0-9._:-]{16,128}")) String key,
            @Valid @RequestBody WriteRequest request) {
        return response(calendar.create(jwt, calendarId, request, key), HttpStatus.CREATED);
    }

    @PutMapping(value = "/{calendarId}/events/{eventId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "updateCalendarEvent", summary = "Replace supported event content with a strong version precondition")
    @ApiResponse(responseCode = "200", description = "Updated event.", headers = @Header(name = "ETag", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "412", description = "The current event version differs.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "428", description = "If-Match is missing.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "415", description = "Content-Type must be application/json.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Event> update(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId, @PathVariable String eventId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Parameter(required = true, description = "Exactly one strong version returned by this API; wildcard and weak versions are rejected.") String version,
            @Valid @RequestBody WriteRequest request) {
        return response(calendar.update(jwt, calendarId, eventId, request, version), HttpStatus.OK);
    }

    @DeleteMapping("/{calendarId}/events/{eventId}")
    @Operation(operationId = "deleteCalendarEvent", summary = "Delete an event with a strong version precondition")
    @ApiResponse(responseCode = "204", description = "Event deleted.", content = @Content)
    @ApiResponse(responseCode = "412", description = "The current event version differs.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "428", description = "If-Match is missing.", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    public ResponseEntity<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable String calendarId, @PathVariable String eventId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) @Parameter(required = true, description = "Exactly one strong version returned by this API.") String version) {
        calendar.delete(jwt, calendarId, eventId, version);
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }

    private ResponseEntity<Event> response(Event event, HttpStatus status) {
        return ResponseEntity.status(status).eTag(event.version()).cacheControl(CacheControl.noStore()).body(event);
    }
}
