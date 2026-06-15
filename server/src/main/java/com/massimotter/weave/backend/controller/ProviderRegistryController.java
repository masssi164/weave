package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.provider.DomainBindingsResponse;
import com.massimotter.weave.backend.provider.ProviderRegistry;
import com.massimotter.weave.backend.provider.ProviderRegistryResponse;
import com.massimotter.weave.backend.service.WorkspaceCapabilityService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Provider registry", description = "Backend-owned provider capability/readiness registry for optional provider-stack modules.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective workspace capability policy denies provider readiness access.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class ProviderRegistryController {

    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;

    public ProviderRegistryController(ProviderRegistry providerRegistry,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this.providerRegistry = providerRegistry;
        this.workspaceCapabilityService = workspaceCapabilityService;
    }

    @GetMapping("/api/providers/status")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(summary = "Read support-safe admin/provider category capability and readiness status")
    @ApiResponse(responseCode = "200", description = "Provider registry snapshot.",
            content = @Content(schema = @Schema(implementation = ProviderRegistryResponse.class)))
    public ProviderRegistryResponse status(@AuthenticationPrincipal Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "provider-registry", "status");
        return providerRegistry.status();
    }

    @GetMapping({"/api/admin/domains/bindings", "/api/admin/domains/{domainKey}/bindings"})
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(summary = "Read generic domain binding and provider connection readiness state")
    @ApiResponse(responseCode = "200", description = "Provider-neutral domain binding snapshot.",
            content = @Content(schema = @Schema(implementation = DomainBindingsResponse.class)))
    public DomainBindingsResponse domainBindings(@AuthenticationPrincipal Jwt jwt,
            @PathVariable(name = "domainKey", required = false) String domainKey) {
        workspaceCapabilityService.requireCapability(jwt, "admin_control_plane.readiness_read", "domain-bindings", "status");
        return providerRegistry.domainBindings(domainKey);
    }
}
