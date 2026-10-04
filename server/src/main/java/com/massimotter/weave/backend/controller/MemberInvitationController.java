package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.identity.MemberInvitationRequest;
import com.massimotter.weave.backend.model.identity.MemberInvitationResponse;
import com.massimotter.weave.backend.service.MemberInvitationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/organizations/{organizationId}/invitations")
@PreAuthorize("hasAuthority('SCOPE_weave:workspace') and (hasRole('OWNER') or hasRole('ADMIN'))")
@Tag(name = "Organization invitations", description = "Organization-scoped member invitations.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "The caller cannot administer this organization.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class MemberInvitationController {
    private final MemberInvitationService service;

    public MemberInvitationController(MemberInvitationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(operationId = "createOrganizationInvitation")
    public MemberInvitationResponse create(@PathVariable String organizationId,
            @Valid @RequestBody MemberInvitationRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        return service.create(organizationId, request, idempotencyKey, jwt);
    }

    @GetMapping
    @Operation(operationId = "listOrganizationInvitations")
    @ApiResponse(responseCode = "200", description = "Organization invitations.",
            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MemberInvitationResponse.class))))
    public List<MemberInvitationResponse> list(@PathVariable String organizationId, @AuthenticationPrincipal Jwt jwt) {
        return service.list(organizationId, jwt);
    }

    @PostMapping("/{invitationHandle}/resend")
    @Operation(operationId = "resendOrganizationInvitation")
    @ApiResponse(responseCode = "200", description = "Resent organization invitation.",
            content = @Content(schema = @Schema(implementation = MemberInvitationResponse.class)))
    public MemberInvitationResponse resend(@PathVariable String organizationId, @PathVariable String invitationHandle,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        return service.resend(organizationId, invitationHandle, idempotencyKey, jwt);
    }

    @DeleteMapping("/{invitationHandle}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(operationId = "revokeOrganizationInvitation")
    public void revoke(@PathVariable String organizationId, @PathVariable String invitationHandle,
            @RequestHeader("Idempotency-Key") String idempotencyKey, @AuthenticationPrincipal Jwt jwt) {
        service.revoke(organizationId, invitationHandle, idempotencyKey, jwt);
    }
}
