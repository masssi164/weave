package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.service.AdminProviderRegistryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Provider registry", description = "Backend-owned provider capability/readiness registry for optional provider-stack modules.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Missing workspace scope, wrong deployment organization, or denied Admin readiness capability.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ProviderRegistryController {

    private final AdminProviderRegistryService providerRegistry;

    public ProviderRegistryController(AdminProviderRegistryService providerRegistry) {
        this.providerRegistry = providerRegistry;
    }

    @GetMapping("/api/admin/providers/status")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "status", summary = "Read support-safe admin/provider category capability and readiness status")
    @ApiResponse(responseCode = "200", description = "Deployment provider configuration and the configured organization's actual Files binding status.",
            content = @Content(schema = @Schema(implementation = ProviderRegistryResponse.class)))
    public ProviderRegistryResponse status(@AuthenticationPrincipal Jwt jwt) {
        return providerRegistry.status(jwt);
    }
}
