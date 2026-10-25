package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.calendar.CalendarClientSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarNativeSyncSetupResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialListResponse;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialRequest;
import com.massimotter.weave.backend.model.calendar.CalendarSetupCredentialResponse;
import com.massimotter.weave.backend.service.CalendarFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.calendar.AppleMobileConfigProfile;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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

/** Historical public DAV setup surface for explicitly opted-in compatibility environments. */
@RestController
@Hidden
@Validated
@ConditionalOnProperty(name = "weave.compatibility.public-dav.enabled", havingValue = "true")
public class CalendarDavSetupController {

    private final CalendarFacadeService calendarFacadeService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public CalendarDavSetupController(CalendarFacadeService calendarFacadeService,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.calendarFacadeService = calendarFacadeService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/calendar/client-setup")
    public CalendarClientSetupResponse clientSetup() {
        return calendarFacadeService.clientSetup();
    }

    @GetMapping("/api/calendar/native-sync-setup")
    public CalendarNativeSyncSetupResponse nativeSyncSetup(@AuthenticationPrincipal Jwt jwt) {
        return calendarFacadeService.nativeSyncSetup(workspaceCapabilityService.snapshot(jwt).calendar());
    }

    @GetMapping("/api/calendar/client-setup/credentials")
    public CalendarSetupCredentialListResponse setupCredentials() {
        return calendarFacadeService.setupCredentials();
    }

    @PostMapping("/api/calendar/client-setup/credentials")
    public CalendarSetupCredentialResponse createSetupCredential(
            @Valid @RequestBody CalendarSetupCredentialRequest request) {
        return calendarFacadeService.createSetupCredential(request);
    }

    @DeleteMapping("/api/calendar/client-setup/credentials/{credentialId}")
    public CalendarSetupCredentialResponse revokeSetupCredential(@PathVariable @Size(max = 128) String credentialId) {
        return calendarFacadeService.revokeSetupCredential(credentialId);
    }

    @GetMapping(value = "/api/calendar/client-setup/apple.mobileconfig",
            produces = "application/x-apple-aspen-config")
    public ResponseEntity<byte[]> appleMobileConfigProfile() {
        AppleMobileConfigProfile profile = calendarFacadeService.appleMobileConfigProfile();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/x-apple-aspen-config"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + profile.filename() + "\"")
                .body(profile.content());
    }
}
