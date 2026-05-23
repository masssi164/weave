package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.OnboardingStatusResponse;
import com.massimotter.weave.backend.service.OnboardingStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Onboarding", description = "First-run user invite and module provisioning status.")
public class OnboardingController {

    private final OnboardingStatusService onboardingStatusService;

    public OnboardingController(OnboardingStatusService onboardingStatusService) {
        this.onboardingStatusService = onboardingStatusService;
    }

    @GetMapping("/api/onboarding/status")
    @Operation(
            summary = "Get first-run invite and module provisioning status",
            description = "Returns the authenticated caller identity, role mapping, profile readiness, and Matrix/Nextcloud provisioning states for first-run routing.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "First-run onboarding status for the authenticated caller.",
                    content = @Content(schema = @Schema(implementation = OnboardingStatusResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OnboardingStatusResponse status(@AuthenticationPrincipal Jwt jwt) {
        return onboardingStatusService.status(jwt);
    }
}
