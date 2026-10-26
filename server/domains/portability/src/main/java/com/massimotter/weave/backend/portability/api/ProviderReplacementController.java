package com.massimotter.weave.backend.portability.api;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.portability.application.ProviderReplacementDryRunService;
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
public class ProviderReplacementController {
    private final ProviderReplacementDryRunService replacements;

    public ProviderReplacementController(ProviderReplacementDryRunService replacements) {
        this.replacements = replacements;
    }

    @PostMapping("/api/admin/providers/replacements/dry-run")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "dryRunProviderReplacement", summary = "Dry-run a provider replacement before activation")
    @ApiResponse(responseCode = "200", description = "Support-safe provider replacement dry-run report.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProviderReplacementDryRunResponse.class)))
    public ProviderReplacementDryRunResponse dryRunProviderReplacement(
            @Valid @RequestBody ProviderReplacementDryRunRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return replacements.dryRunProviderReplacement(request, jwt);
    }

}
