package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceReleaseReadinessResponse;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceReleaseReadinessService;
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
@Tag(name = "Admin Workspace", description = "Admin-only configuration readiness and policy diagnostics.")
public class AdminWorkspaceController {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceReleaseReadinessService workspaceReleaseReadinessService;

    public AdminWorkspaceController(WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceReleaseReadinessService workspaceReleaseReadinessService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceReleaseReadinessService = workspaceReleaseReadinessService;
    }

    @GetMapping("/api/admin/workspace/capability-policy")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(
            operationId = "capabilityPolicy",
            summary = "Get Admin workspace capability policy",
            description = "Returns an admin/operator support-safe snapshot of IDM role/group intake, profile mapping, deny-by-default posture, and Weaver-disabled-by-default policy state.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Workspace capability policy snapshot.",
                    content = @Content(schema = @Schema(implementation = WorkspaceCapabilityPolicyResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Admin Console bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Workspace scope, current deployment organization, selected owner/admin role or readiness capability is missing.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceCapabilityPolicyResponse capabilityPolicy(@AuthenticationPrincipal Jwt jwt) {
        return workspaceCapabilityService.policySnapshot(jwt);
    }

    @GetMapping("/api/admin/workspace/release-readiness")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(
            operationId = "releaseReadiness",
            summary = "Get Admin workspace configuration readiness",
            description = "Returns the configured organization's operator-facing configuration and cached capability snapshot. This read does not verify live provider operations or release eligibility.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Admin workspace configuration snapshot; not live provider or release verification.",
                    content = @Content(schema = @Schema(implementation = WorkspaceReleaseReadinessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid Admin Console bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Workspace scope, current deployment organization, selected owner/admin role or readiness capability is missing.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceReleaseReadinessResponse releaseReadiness(@AuthenticationPrincipal Jwt jwt) {
        return workspaceReleaseReadinessService.snapshot(jwt);
    }

}
