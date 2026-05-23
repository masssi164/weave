package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.AuthenticatedUserResponse;
import com.massimotter.weave.backend.service.ProductProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Identity", description = "Authenticated caller introspection endpoints.")
public class IdentityController {

    private final ProductProfileService productProfileService;

    public IdentityController(ProductProfileService productProfileService) {
        this.productProfileService = productProfileService;
    }

    @GetMapping("/api/me")
    @Operation(
            summary = "Get the authenticated caller profile",
            description = "Returns the canonical Weave identity and profile snapshot available to the backend.",
            security = @SecurityRequirement(name = "bearer-jwt"))
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authenticated caller details.",
                    content = @Content(schema = @Schema(implementation = AuthenticatedUserResponse.class))),
            @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public AuthenticatedUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        return productProfileService.authenticatedUser(jwt);
    }
}
