package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.OrganizationManifestResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyResponse;
import com.massimotter.weave.backend.model.WorkspaceHomeResponse;
import com.massimotter.weave.backend.model.WorkspaceReleaseReadinessResponse;
import com.massimotter.weave.backend.service.OrganizationManifestService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceHomeService;
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
@Tag(name = "Workspace", description = "Workspace readiness and capability endpoints.")
public class WorkspaceController {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceReleaseReadinessService workspaceReleaseReadinessService;
    private final WorkspaceHomeService workspaceHomeService;
    private final OrganizationManifestService organizationManifestService;

    public WorkspaceController(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceReleaseReadinessService workspaceReleaseReadinessService,
            WorkspaceHomeService workspaceHomeService,
            OrganizationManifestService organizationManifestService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceReleaseReadinessService = workspaceReleaseReadinessService;
        this.workspaceHomeService = workspaceHomeService;
        this.organizationManifestService = organizationManifestService;
    }

    @GetMapping({"/api/organization/manifest", "/api/v1/organization/manifest"})
    @Operation(
            summary = "Get authenticated organization manifest",
            description = "Returns the support-safe org manifest consumed by Weave Client after org URL discovery and SSO. Provider setup, endpoint rotation, diagnostics, policy authoring, and whitelisting remain owned by the Organization/Admin Console.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Support-safe organization manifest for the authenticated member client.",
                    content = @Content(schema = @Schema(implementation = OrganizationManifestResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "Organization auth URL is invalid or support-unsafe.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public OrganizationManifestResponse organizationManifest(@AuthenticationPrincipal Jwt jwt) {
        return organizationManifestService.manifestFor(jwt);
    }

    @GetMapping({"/api/workspace/capabilities", "/api/v1/workspace/capabilities"})
    @Operation(
            summary = "Get workspace capability readiness",
            description = "Returns the backend-owned workspace capability snapshot consumed by the Weave client.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Workspace capability snapshot.",
                    content = @Content(schema = @Schema(implementation = WorkspaceCapabilitiesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceCapabilitiesResponse capabilities(@AuthenticationPrincipal Jwt jwt) {
        return workspaceCapabilityService.snapshot(jwt);
    }

    @GetMapping({"/api/workspace/capability-policy", "/api/v1/workspace/capability-policy"})
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(
            summary = "Get workspace capability policy",
            description = "Returns an admin/operator support-safe snapshot of IDM role/group intake, profile mapping, deny-by-default posture, and Weaver-disabled-by-default policy state.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Workspace capability policy snapshot.",
                    content = @Content(schema = @Schema(implementation = WorkspaceCapabilityPolicyResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective policy denies capability-policy access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceCapabilityPolicyResponse capabilityPolicy(@AuthenticationPrincipal Jwt jwt) {
        return workspaceCapabilityService.policySnapshot(jwt);
    }

    @GetMapping({"/api/workspace/release-readiness", "/api/v1/workspace/release-readiness"})
    @Operation(
            summary = "Get workspace readiness",
            description = "Returns an operator-facing snapshot of the backend-owned core dependencies and remaining setup actions.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Workspace workspace readiness snapshot.",
                    content = @Content(schema = @Schema(implementation = WorkspaceReleaseReadinessResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective policy denies operator readiness access.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceReleaseReadinessResponse releaseReadiness(@AuthenticationPrincipal Jwt jwt) {
        return workspaceReleaseReadinessService.snapshot(jwt);
    }

    @GetMapping({"/api/workspace/home", "/api/v1/workspace/home"})
    @Operation(
            summary = "Get Weave Home daily-work snapshot",
            description = "Returns the backend-owned, support-safe daily work loop consumed by Weave Home.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Weave Home daily-work snapshot.",
                    content = @Content(schema = @Schema(implementation = WorkspaceHomeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceHomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return workspaceHomeService.snapshot(jwt);
    }
}
