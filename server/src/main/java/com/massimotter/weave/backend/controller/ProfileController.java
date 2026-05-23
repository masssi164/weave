package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.ModuleSyncStatusResponse;
import com.massimotter.weave.backend.model.ProfileReadinessResponse;
import com.massimotter.weave.backend.model.ProductProfileResponse;
import com.massimotter.weave.backend.model.UpdateProductProfileRequest;
import com.massimotter.weave.backend.service.ProductProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Profile", description = "Authenticated product profile facade endpoints.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ProfileController {

    private final ProductProfileService productProfileService;

    public ProfileController(ProductProfileService productProfileService) {
        this.productProfileService = productProfileService;
    }

    @GetMapping("/api/profile")
    @Operation(
            summary = "Get the authenticated product profile",
            description = "Returns the product-owned profile facade for the authenticated caller.")
    @ApiResponse(responseCode = "200", description = "Product profile snapshot.",
            content = @Content(schema = @Schema(implementation = ProductProfileResponse.class)))
    public ProductProfileResponse profile(@AuthenticationPrincipal Jwt jwt) {
        return productProfileService.profile(jwt);
    }

    @PatchMapping("/api/profile")
    @Operation(
            summary = "Update the authenticated product profile",
            description = "Partially updates mutable product profile fields and returns the updated profile snapshot.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Updated product profile snapshot.",
                    content = @Content(schema = @Schema(implementation = ProductProfileResponse.class))),
            @ApiResponse(responseCode = "400", description = "Profile update validation failed.",
                    content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProductProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody UpdateProductProfileRequest request) {
        return productProfileService.update(jwt, request);
    }

    @GetMapping("/api/profile/sync-status")
    @Operation(
            summary = "Get product profile module sync status",
            description = "Returns frontend-safe Matrix and Nextcloud profile synchronization state for the authenticated caller.")
    @ApiResponse(responseCode = "200", description = "Profile sync status.",
            content = @Content(schema = @Schema(implementation = ModuleSyncStatusResponse.class)))
    public ModuleSyncStatusResponse syncStatus(@AuthenticationPrincipal Jwt jwt) {
        return productProfileService.syncStatus(jwt);
    }

    @GetMapping("/api/profile/readiness")
    @Operation(
            summary = "Get product profile facade readiness",
            description = "Returns support-safe readiness for the backend-owned profile facade without exposing provider credentials or upstream URLs.")
    @ApiResponse(responseCode = "200", description = "Profile facade readiness.",
            content = @Content(schema = @Schema(implementation = ProfileReadinessResponse.class)))
    public ProfileReadinessResponse readiness(@AuthenticationPrincipal Jwt jwt) {
        return productProfileService.readiness(jwt);
    }
}
