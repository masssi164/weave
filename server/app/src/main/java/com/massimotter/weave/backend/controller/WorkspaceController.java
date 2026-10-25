package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceCapabilitiesResponse;
import com.massimotter.weave.backend.capability.api.WorkspaceHomeResponse;
import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.service.WorkspaceHomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Workspace", description = "Workspace readiness and capability endpoints.")
public class WorkspaceController {

    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final WorkspaceHomeService workspaceHomeService;

    public WorkspaceController(
            WorkspaceCapabilityService workspaceCapabilityService,
            WorkspaceHomeService workspaceHomeService) {
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.workspaceHomeService = workspaceHomeService;
    }

    @GetMapping("/api/workspace/capabilities")
    @Operation(
            operationId = "capabilities",
            summary = "Get workspace capability readiness",
            description = "Returns the backend-owned workspace capability snapshot consumed by the Weave client.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Workspace capability snapshot.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkspaceCapabilitiesResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceCapabilitiesResponse capabilities(@AuthenticationPrincipal Jwt jwt) {
        return workspaceCapabilityService.snapshot(jwt);
    }

    @GetMapping("/api/workspace/home")
    @Operation(
            operationId = "home",
            summary = "Get Weave Home daily-work snapshot",
            description = "Returns the backend-owned, support-safe daily work loop consumed by Weave Home.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Weave Home daily-work snapshot.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = WorkspaceHomeResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public WorkspaceHomeResponse home(@AuthenticationPrincipal Jwt jwt) {
        return workspaceHomeService.snapshot(jwt);
    }
}
