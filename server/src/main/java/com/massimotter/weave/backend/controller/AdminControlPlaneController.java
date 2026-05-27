package com.massimotter.weave.backend.controller;

import com.massimotter.weave.backend.model.ApiErrorResponse;
import com.massimotter.weave.backend.model.admin.AdminAuditEventResponse;
import com.massimotter.weave.backend.model.admin.AdminControlPlaneResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.model.admin.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.model.admin.EffectivePolicyResponse;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapRequest;
import com.massimotter.weave.backend.model.admin.OrganizationBootstrapResponse;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.model.admin.ProviderReadinessTestResponse;
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
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or is not an owner/admin/operator.",
                content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
})
public class AdminControlPlaneController {

    private final AdminControlPlaneService adminControlPlaneService;

    public AdminControlPlaneController(AdminControlPlaneService adminControlPlaneService) {
        this.adminControlPlaneService = adminControlPlaneService;
    }

    @GetMapping({"/api/admin/control-plane", "/api/v1/admin/control-plane"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Read the support-safe organization control-plane overview")
    @ApiResponse(responseCode = "200", description = "Admin control-plane snapshot.",
            content = @Content(schema = @Schema(implementation = AdminControlPlaneResponse.class)))
    public AdminControlPlaneResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.overview(jwt);
    }

    @GetMapping({"/api/admin/policies/capability-whitelist", "/api/v1/admin/policies/capability-whitelist"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Read deny-by-default capability whitelist policy")
    @ApiResponse(responseCode = "200", description = "Capability whitelist snapshot.",
            content = @Content(schema = @Schema(implementation = CapabilityWhitelistResponse.class)))
    public CapabilityWhitelistResponse whitelist(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.whitelist(jwt);
    }

    @GetMapping({"/api/admin/policies/effective", "/api/v1/admin/policies/effective"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Explain the effective capability policy for the authenticated subject")
    @ApiResponse(responseCode = "200", description = "Support-safe effective policy explanation.",
            content = @Content(schema = @Schema(implementation = EffectivePolicyResponse.class)))
    public EffectivePolicyResponse effectivePolicy(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.effectivePolicy(jwt);
    }

    @PostMapping({"/api/admin/organizations/bootstrap", "/api/v1/admin/organizations/bootstrap"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Bootstrap or bind an organization with immutable identity recovery administrators")
    @ApiResponse(responseCode = "200", description = "Support-safe organization bootstrap result.",
            content = @Content(schema = @Schema(implementation = OrganizationBootstrapResponse.class)))
    public OrganizationBootstrapResponse bootstrapOrganization(
            @Valid @RequestBody OrganizationBootstrapRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.bootstrapOrganization(request, jwt);
    }

    @PatchMapping({"/api/admin/policies/capability-whitelist", "/api/v1/admin/policies/capability-whitelist"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Record a support-safe capability whitelist policy update")
    @ApiResponse(responseCode = "200", description = "Updated capability whitelist snapshot.",
            content = @Content(schema = @Schema(implementation = CapabilityWhitelistResponse.class)))
    public CapabilityWhitelistResponse updateWhitelist(
            @Valid @RequestBody CapabilityWhitelistUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.updateWhitelist(request, jwt);
    }

    @PostMapping({"/api/admin/providers/selections", "/api/v1/admin/providers/selections"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN')")
    @Operation(summary = "Apply or dry-run an Admin Console selected provider mapping")
    @ApiResponse(responseCode = "200", description = "Support-safe selected provider mapping.",
            content = @Content(schema = @Schema(implementation = ProviderSelectionResponse.class)))
    public ProviderSelectionResponse selectProvider(
            @Valid @RequestBody ProviderSelectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.selectProvider(request, jwt);
    }

    @PostMapping({"/api/admin/providers/readiness-tests", "/api/v1/admin/providers/readiness-tests"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Run a backend-owned support-safe provider readiness test contract")
    @ApiResponse(responseCode = "200", description = "Support-safe provider readiness test result.",
            content = @Content(schema = @Schema(implementation = ProviderReadinessTestResponse.class)))
    public ProviderReadinessTestResponse testProviderReadiness(
            @Valid @RequestBody ProviderReadinessTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.testProviderReadiness(request, jwt);
    }

    @GetMapping({"/api/admin/audit/events", "/api/v1/admin/audit/events"})
    @PreAuthorize("hasAnyRole('OWNER','ADMIN','OPERATOR')")
    @Operation(summary = "Read support-safe admin/provider audit events")
    @ApiResponse(responseCode = "200", description = "Audit event list.")
    public List<AdminAuditEventResponse> auditEvents() {
        return adminControlPlaneService.auditEvents();
    }
}
