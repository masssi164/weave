package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicySimulationResponse;
import com.massimotter.weave.backend.model.admin.PlatformIdentityReadinessResponse;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapResponse;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.model.admin.ProviderSelectionRequest;
import com.massimotter.weave.backend.model.admin.ProviderSelectionResponse;
import com.massimotter.weave.backend.service.AdminControlPlaneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Admin control plane", description = "Organization/Admin Console APIs for provider policy, readiness, SecretRefs, and audit.")
@SecurityRequirement(name = "bearer-jwt")
@ApiResponses({
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective workspace capability policy denies the operation.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminControlPlaneController {

    private final AdminControlPlaneService adminControlPlaneService;

    public AdminControlPlaneController(AdminControlPlaneService adminControlPlaneService) {
        this.adminControlPlaneService = adminControlPlaneService;
    }

    @GetMapping("/api/admin/control-plane")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getAdminControlPlane", summary = "Read the support-safe organization control-plane overview")
    @ApiResponse(responseCode = "200", description = "Admin control-plane snapshot.",
            content = @Content(schema = @Schema(implementation = AdminControlPlaneResponse.class)))
    public AdminControlPlaneResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.overview(jwt);
    }

    @GetMapping("/api/admin/policies/capability-whitelist")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getCapabilityWhitelist", summary = "Read deny-by-default capability whitelist policy")
    @ApiResponse(responseCode = "200", description = "Capability whitelist snapshot.",
            content = @Content(schema = @Schema(implementation = CapabilityWhitelistResponse.class)))
    public CapabilityWhitelistResponse whitelist(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.whitelist(jwt);
    }

    @GetMapping("/api/admin/policies/effective")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getEffectivePolicy", summary = "Explain the effective capability policy for the authenticated subject")
    @ApiResponse(responseCode = "200", description = "Support-safe effective policy explanation.",
            content = @Content(schema = @Schema(implementation = EffectivePolicyResponse.class)))
    public EffectivePolicyResponse effectivePolicy(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.effectivePolicy(jwt);
    }

    @PostMapping("/api/admin/policies/effective/simulations")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "simulateEffectivePolicy", summary = "Simulate effective capability policy before provider/realm changes")
    @ApiResponse(responseCode = "200", description = "Support-safe policy simulation.",
            content = @Content(schema = @Schema(implementation = EffectivePolicySimulationResponse.class)))
    public EffectivePolicySimulationResponse simulateEffectivePolicy(
            @Valid @RequestBody EffectivePolicySimulationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.simulateEffectivePolicy(request, jwt);
    }

    @GetMapping("/api/admin/platform/identity/readiness")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getPlatformIdentityReadiness", summary = "Read support-safe fixed Keycloak platform readiness")
    @ApiResponse(responseCode = "200", description = "Backend-owned platform identity readiness.",
            content = @Content(schema = @Schema(implementation = PlatformIdentityReadinessResponse.class)))
    public PlatformIdentityReadinessResponse platformIdentityReadiness(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.platformIdentityReadiness(jwt);
    }

    @PostMapping("/api/admin/organizations/bootstrap")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "bootstrapOrganization", summary = "Bootstrap or bind an organization with immutable identity recovery administrators")
    @ApiResponse(responseCode = "200", description = "Support-safe organization bootstrap result.",
            content = @Content(schema = @Schema(implementation = OrganizationBootstrapResponse.class)))
    public OrganizationBootstrapResponse bootstrapOrganization(
            @Valid @RequestBody OrganizationBootstrapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.bootstrapOrganization(request, jwt);
    }

    @PatchMapping("/api/admin/policies/capability-whitelist")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "updateCapabilityWhitelist", summary = "Record a support-safe capability whitelist policy update")
    @ApiResponse(responseCode = "200", description = "Updated capability whitelist snapshot.",
            content = @Content(schema = @Schema(implementation = CapabilityWhitelistResponse.class)))
    public CapabilityWhitelistResponse updateWhitelist(
            @Valid @RequestBody CapabilityWhitelistUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.updateWhitelist(request, jwt);
    }

    @PostMapping("/api/admin/providers/selections")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "selectProvider", summary = "Apply or dry-run an Admin Console selected provider mapping")
    @ApiResponse(responseCode = "200", description = "Support-safe selected provider mapping.",
            content = @Content(schema = @Schema(implementation = ProviderSelectionResponse.class)))
    public ProviderSelectionResponse selectProvider(
            @Valid @RequestBody ProviderSelectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.selectProvider(request, jwt);
    }

    @PostMapping("/api/admin/providers/readiness-tests")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "testProviderReadiness", summary = "Run a backend-owned support-safe provider readiness test contract")
    @ApiResponse(responseCode = "200", description = "Support-safe provider readiness test result.",
            content = @Content(schema = @Schema(implementation = ProviderReadinessTestResponse.class)))
    public ProviderReadinessTestResponse testProviderReadiness(
            @Valid @RequestBody ProviderReadinessTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.testProviderReadiness(request, jwt);
    }

    @PostMapping("/api/admin/providers/replacements/dry-run")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "dryRunProviderReplacement", summary = "Dry-run a provider replacement before activation")
    @ApiResponse(responseCode = "200", description = "Support-safe provider replacement dry-run report.",
            content = @Content(schema = @Schema(implementation = ProviderReplacementDryRunResponse.class)))
    public ProviderReplacementDryRunResponse dryRunProviderReplacement(
            @Valid @RequestBody ProviderReplacementDryRunRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.dryRunProviderReplacement(request, jwt);
    }

    @GetMapping("/api/admin/audit/events")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "listAdminAuditEvents", summary = "Read support-safe admin/provider audit events")
    @ApiResponse(responseCode = "200", description = "Audit event list.")
    public List<AdminAuditEventResponse> auditEvents(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.auditEvents(jwt);
    }
}
