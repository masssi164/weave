package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.devops.DevopsSummaryResponse;
import com.massimotter.weave.backend.service.DevopsFacadeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "DevOps facade", description = "Provider-neutral read-only DevOps facade for the represented GitLab CE/FOSS adapter.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class DevopsController {

    private final DevopsFacadeService devopsFacadeService;

    public DevopsController(DevopsFacadeService devopsFacadeService) {
        this.devopsFacadeService = devopsFacadeService;
    }

    @GetMapping("/api/workspaces/{workspaceId}/channels/{channelId}/devops/summary")
    @Operation(summary = "Read provider-neutral DevOps summary")
    @ApiResponse(responseCode = "200", description = "Read-only support-safe DevOps summary.",
            content = @Content(schema = @Schema(implementation = DevopsSummaryResponse.class)))
    public DevopsSummaryResponse summary(
            @PathVariable @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String workspaceId,
            @PathVariable @Size(max = 128) @Pattern(regexp = "[A-Za-z0-9._:-]+") String channelId) {
        return devopsFacadeService.summary(workspaceId, channelId);
    }
}
