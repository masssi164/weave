package com.massimotter.weave.backend.meetings.api;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
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
@Tag(name = "Chat domain", description = "Canonical Weave Chat facade with provider-neutral conversations/messages, Context/Space authorization, capability checks, audit, and support-safe provider replacement seams.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing workspace scope, Context/Space access, or required chat capability.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Chat domain facade is disabled, degraded, or not ready.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class MeetingCapsuleController {
    private final com.massimotter.weave.backend.meetings.application.MeetingCapsuleService service;

    public MeetingCapsuleController(com.massimotter.weave.backend.meetings.application.MeetingCapsuleService service) {
        this.service = service;
    }

    @GetMapping("/api/chat/conversations/{conversationId}/meeting-capsules")
    @Operation(operationId = "meetingCapsules", summary = "Read channel Meeting Capsules")
    @ApiResponse(responseCode = "200", description = "Durable channel Meeting Capsules with fail-closed media controls.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MeetingCapsulesResponse.class)))
    public MeetingCapsulesResponse meetingCapsules(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId) {
        return service.meetingCapsules(jwt, conversationId);
    }

    @PostMapping("/api/chat/conversations/{conversationId}/meeting-capsules")
    @Operation(operationId = "createMeetingCapsule", summary = "Create a channel Meeting Capsule")
    @ApiResponse(responseCode = "200", description = "Created Meeting Capsule.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = MeetingCapsuleResponse.class)))
    public MeetingCapsuleResponse createMeetingCapsule(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Size(max = 128) String conversationId,
            @Valid @RequestBody MeetingCapsuleCreateRequest request) {
        return service.createMeetingCapsule(jwt, conversationId, request);
    }
}
