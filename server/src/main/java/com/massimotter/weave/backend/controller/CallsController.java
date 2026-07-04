package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundarySetupResponse;
import com.massimotter.weave.backend.service.CallsFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@Tag(name = "Calls", description = "Authenticated product calls/meetings facade and native call boundary contracts.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class CallsController {

    private final CallsFacadeService callsFacadeService;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public CallsController(CallsFacadeService callsFacadeService,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.callsFacadeService = callsFacadeService;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/calls/native-boundary-setup")
    @Operation(
            operationId = "getCallsNativeBoundarySetup",
            summary = "Describe native Calls and Meetings boundary setup",
            description = "Returns support-safe CallKit/PushKit and Android Telecom/ConnectionService setup metadata backed only by Weave-owned meeting grants and control-plane endpoints.")
    @ApiResponse(responseCode = "200", description = "Native Calls/Meetings boundary metadata.",
            content = @Content(schema = @Schema(implementation = CallNativeBoundarySetupResponse.class)))
    public CallNativeBoundarySetupResponse nativeBoundarySetup(@AuthenticationPrincipal Jwt jwt) {
        return callsFacadeService.nativeBoundarySetup(workspaceCapabilityService.snapshot(jwt).meetingsCalls());
    }
}
