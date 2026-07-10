package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.calls.CallCreateRequest;
import com.massimotter.weave.backend.model.calls.CallJoinRequest;
import com.massimotter.weave.backend.model.calls.CallJoinResponse;
import com.massimotter.weave.backend.model.calls.CallLeaveResponse;
import com.massimotter.weave.backend.model.calls.CallNativeBoundarySetupResponse;
import com.massimotter.weave.backend.model.calls.CallResponse;
import com.massimotter.weave.backend.service.CallsFacadeService;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping("/api/calls")
    @Operation(
            operationId = "createCall",
            summary = "Create a Weave Calls control-plane record",
            description = "Creates provider-neutral call state. Media access still requires a short-lived join grant.")
    @ApiResponse(responseCode = "200", description = "Created Weave call.",
            content = @Content(schema = @Schema(implementation = CallResponse.class)))
    public CallResponse create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody(required = false) CallCreateRequest request) {
        workspaceCapabilityService.requireCapability(jwt, "meetings.host", "calls", "create-call");
        return callsFacadeService.createCall(request);
    }

    @GetMapping("/api/calls/{id}")
    @Operation(operationId = "getCall", summary = "Read Weave Calls control-plane state")
    @ApiResponse(responseCode = "200", description = "Weave call state.",
            content = @Content(schema = @Schema(implementation = CallResponse.class)))
    public CallResponse read(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String id) {
        workspaceCapabilityService.requireCapability(jwt, "meetings.join", "calls", "read-call");
        return callsFacadeService.getCall(id);
    }

    @PostMapping("/api/calls/{id}/join")
    @Operation(
            operationId = "joinCall",
            summary = "Create a short-lived call join grant",
            description = "Returns a scoped join grant for the configured media provider without exposing provider API keys or backend service credentials.")
    @ApiResponse(responseCode = "200", description = "Short-lived call join grant.",
            content = @Content(schema = @Schema(implementation = CallJoinResponse.class)))
    public CallJoinResponse join(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String id,
            @Valid @RequestBody(required = false) CallJoinRequest request) {
        workspaceCapabilityService.requireCapability(jwt, "meetings.join", "calls", "join-call");
        return callsFacadeService.joinCall(id, request, jwt);
    }

    @PostMapping("/api/calls/{id}/leave")
    @Operation(operationId = "leaveCall", summary = "Leave a Weave call")
    @ApiResponse(responseCode = "200", description = "Call leave acknowledgement.",
            content = @Content(schema = @Schema(implementation = CallLeaveResponse.class)))
    public CallLeaveResponse leave(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String id) {
        workspaceCapabilityService.requireCapability(jwt, "meetings.join", "calls", "leave-call");
        return callsFacadeService.leaveCall(id, jwt);
    }

    @PostMapping("/api/calls/{id}/end")
    @Operation(operationId = "endCall", summary = "End a Weave call")
    @ApiResponse(responseCode = "200", description = "Ended Weave call.",
            content = @Content(schema = @Schema(implementation = CallResponse.class)))
    public ResponseEntity<CallResponse> end(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String id) {
        workspaceCapabilityService.requireCapability(jwt, "meetings.host", "calls", "end-call");
        return ResponseEntity.ok(callsFacadeService.endCall(id));
    }
}
