package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionRequest;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionResponse;
import com.massimotter.weave.backend.model.spaces.SpaceMemberChangeRequest;
import com.massimotter.weave.backend.model.spaces.SpaceMemberResponse;
import com.massimotter.weave.backend.model.spaces.SpaceMemberListResponse;
import com.massimotter.weave.backend.service.spaces.SpaceAdminApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Spaces Admin", description = "Explicit durable Space setup for the current organization.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Owner/admin policy or organization denied.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "409", description = "Existing Space differs from setup intent.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class SpacesAdminController {
    private final SpaceAdminApiService spaces;

    public SpacesAdminController(SpaceAdminApiService spaces) {
        this.spaces = spaces;
    }

    @PostMapping(value = "/api/admin/spaces", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "provisionSpace", summary = "Explicitly provision current-organization Space membership")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Created or confirmed identical durable setup.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceProvisionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid Space setup request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SpaceProvisionResponse provision(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody SpaceProvisionRequest request) {
        return spaces.provision(jwt, request);
    }

    @GetMapping(value = "/api/admin/spaces/{spaceRef}/members", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listSpaceMembers", summary = "Page current Space member grants")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current member grants in account-reference order.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceMemberListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page request.")
    })
    public SpaceMemberListResponse listMembers(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String spaceRef,
            @RequestParam(required = false) String afterAccountRef,
            @RequestParam(defaultValue = "50") int limit) {
        return spaces.listMembers(jwt, spaceRef, afterAccountRef, limit);
    }

    @GetMapping(value = "/api/admin/spaces/{spaceRef}/members/{accountRef}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getSpaceMember", summary = "Read current Space member grant and version")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current member grant.",
                    headers = @Header(name = "ETag", description = "Strong current member version."),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceMemberResponse.class))),
            @ApiResponse(responseCode = "404", description = "Current member unavailable."),
            @ApiResponse(responseCode = "410", description = "Revoked grant; ETag identifies the tombstone for explicit regrant.",
                    headers = @Header(name = "ETag", description = "Strong revoked member version."),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ResponseEntity<SpaceMemberResponse> getMember(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String spaceRef, @PathVariable String accountRef) {
        var member = spaces.getMember(jwt, spaceRef, accountRef);
        return ResponseEntity.ok().eTag(member.strongEtag())
                .body(SpaceAdminApiService.response(member));
    }

    @PutMapping(value = "/api/admin/spaces/{spaceRef}/members/{accountRef}",
            consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "putSpaceMember", summary = "Grant or replace current Space membership")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Versioned current member grant.",
                    headers = @Header(name = "ETag", description = "Strong current member version required for replacement or revocation."),
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceMemberResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid grant or precondition."),
            @ApiResponse(responseCode = "404", description = "Member unavailable for replacement."),
            @ApiResponse(responseCode = "412", description = "Member precondition is stale."),
            @ApiResponse(responseCode = "428", description = "Member precondition required.")
    })
    public ResponseEntity<SpaceMemberResponse> putMember(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String spaceRef, @PathVariable String accountRef,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestHeader(value = "If-None-Match", required = false) String ifNoneMatch,
            @Valid @RequestBody SpaceMemberChangeRequest request) {
        var result = spaces.grant(jwt, spaceRef, accountRef, request, ifMatch, ifNoneMatch);
        return ResponseEntity.ok().eTag(result.strongEtag())
                .body(SpaceAdminApiService.response(result));
    }

    @DeleteMapping("/api/admin/spaces/{spaceRef}/members/{accountRef}")
    @Operation(operationId = "deleteSpaceMember", summary = "Revoke current Space membership")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Current grant revoked."),
            @ApiResponse(responseCode = "400", description = "Invalid member precondition."),
            @ApiResponse(responseCode = "404", description = "Member unavailable."),
            @ApiResponse(responseCode = "412", description = "Member precondition is stale."),
            @ApiResponse(responseCode = "428", description = "Member precondition required.")
    })
    public ResponseEntity<Void> deleteMember(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String spaceRef, @PathVariable String accountRef,
            @RequestHeader(value = "If-Match", required = false) String ifMatch) {
        spaces.revoke(jwt, spaceRef, accountRef, ifMatch);
        return ResponseEntity.noContent().build();
    }
}
