package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.spaces.SpaceRelationshipListResponse;
import com.massimotter.weave.backend.service.spaces.SpaceRelationshipUserService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Spaces User", description = "Current member access to durable Weave Spaces.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Authentication required.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Current member admission denied.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "404", description = "Space absent or not visible.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "503", description = "Current resource rights cannot be verified.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class SpacesRelationshipsUserController {
    private final SpaceRelationshipUserService relationships;

    public SpacesRelationshipsUserController(SpaceRelationshipUserService relationships) {
        this.relationships = relationships;
    }

    @GetMapping(value = "/api/spaces/{spaceRef}/relationships", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listSpaceRelationships",
            summary = "List direct materialized resource references visible to the current member")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current direct relationships in stable order.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceRelationshipListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid relation page request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SpaceRelationshipListResponse list(@AuthenticationPrincipal Jwt jwt,
            @PathVariable String spaceRef,
            @RequestParam(required = false) String afterRelationRef,
            @RequestParam(defaultValue = "25") int limit) {
        return relationships.list(jwt, spaceRef, afterRelationRef, limit);
    }
}
