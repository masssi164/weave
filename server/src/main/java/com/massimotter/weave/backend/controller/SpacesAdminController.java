package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionRequest;
import com.massimotter.weave.backend.model.spaces.SpaceProvisionResponse;
import com.massimotter.weave.backend.service.spaces.SpaceAdminApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
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
}
