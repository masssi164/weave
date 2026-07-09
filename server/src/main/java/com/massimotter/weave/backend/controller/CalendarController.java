package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calendar.CalendarAccessPolicyResponse;
import com.massimotter.weave.backend.model.calendar.CalendarClientSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarNativeSyncSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarScopesResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Calendar", description = "Authenticated product calendar facade backed by Weave-owned calendar projections.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Calendar storage adapter is not configured or unavailable.",
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

    @GetMapping("/api/calendar/client-setup")
    @Operation(summary = "Describe native calendar client setup options")
    @ApiResponse(responseCode = "200", description = "Secret-free native calendar client setup metadata.",
            content = @Content(schema = @Schema(implementation = CalendarClientSetupResponse.class)))
    public CalendarClientSetupResponse clientSetup() {
        return calendarFacadeService.clientSetup();
    }

    @GetMapping("/api/calendar/native-sync-setup")
    @Operation(
            operationId = "getCalendarNativeSyncSetup",
            summary = "Describe native Calendar setup and sync boundary",
            description = "Returns support-safe iOS Calendar profile and Android Account/SyncAdapter setup metadata backed only by Weave-owned calendar facade endpoints.")
    @ApiResponse(responseCode = "200", description = "Native Calendar setup/sync metadata.",
            content = @Content(schema = @Schema(implementation = CalendarNativeSyncSetupResponse.class)))
    public CalendarNativeSyncSetupResponse nativeSyncSetup(@AuthenticationPrincipal Jwt jwt) {
        return calendarFacadeService.nativeSyncSetup(workspaceCapabilityService.snapshot(jwt).calendar());
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

}
