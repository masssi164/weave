package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.spaces.SpaceUserListResponse;
import com.massimotter.weave.backend.model.spaces.SpaceUserResponse;
import com.massimotter.weave.backend.service.spaces.SpaceUserApiService;
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
                        schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class SpacesUserController {
    private final SpaceUserApiService spaces;

    public SpacesUserController(SpaceUserApiService spaces) {
        this.spaces = spaces;
    }

    @GetMapping(value = "/api/spaces", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "listSpaces", summary = "List current member Spaces")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Visible Spaces in stable order.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = SpaceUserListResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid page request.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public SpaceUserListResponse list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String afterSpaceRef,
            @RequestParam(defaultValue = "25") int limit) {
        return spaces.list(jwt, afterSpaceRef, limit);
    }

    @GetMapping(value = "/api/spaces/{spaceRef}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "getSpace", summary = "Read one current member Space")
    @ApiResponse(responseCode = "200", description = "Visible Space.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = SpaceUserResponse.class)))
    public SpaceUserResponse inspect(@AuthenticationPrincipal Jwt jwt, @PathVariable String spaceRef) {
        return spaces.inspect(jwt, spaceRef);
    }
}
