package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.admin.ProviderCapabilityHealthResponse;
import com.massimotter.weave.backend.service.ProviderCapabilityHealthService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin provider health", description = "Support-safe cached provider capability health for operators and support bundles.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Effective workspace policy denies readiness diagnostics.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ProviderCapabilityHealthController {

    private final ProviderCapabilityHealthService providerHealthService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public ProviderCapabilityHealthController(
            ProviderCapabilityHealthService providerHealthService,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.providerHealthService = providerHealthService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/admin/provider-capability-health")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(
            operationId = "getProviderCapabilityHealth",
            summary = "Read cached support-safe provider capability health")
    @ApiResponse(responseCode = "200", description = "Cached provider capability observations.",
            content = @Content(schema = @Schema(implementation = ProviderCapabilityHealthResponse.class)))
    public ProviderCapabilityHealthResponse health(@AuthenticationPrincipal Jwt jwt) {
        workspaceCapabilityService.requireCapability(
                jwt,
                "admin_control_plane.readiness_read",
                "provider-capability-health",
                "read");
        return providerHealthService.supportSafeSnapshot();
    }
}
