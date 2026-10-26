package com.massimotter.weave.backend.identity.organization.api;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.identity.organization.application.OrganizationBootstrapService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin control plane", description = "Organization/Admin Console APIs for provider policy, readiness, SecretRefs, and audit.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective workspace capability policy denies the operation.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class OrganizationBootstrapController {
    private final OrganizationBootstrapService bootstrap;

    public OrganizationBootstrapController(OrganizationBootstrapService bootstrap) {
        this.bootstrap = bootstrap;
    }

    @PostMapping("/api/admin/organizations/bootstrap")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "bootstrapOrganization", summary = "Bootstrap or bind an organization with immutable identity recovery administrators")
    @ApiResponse(responseCode = "200", description = "Support-safe organization bootstrap result.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = OrganizationBootstrapResponse.class)))
    public OrganizationBootstrapResponse bootstrapOrganization(
            @Valid @RequestBody OrganizationBootstrapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return bootstrap.bootstrapOrganization(request, jwt);
    }

}
