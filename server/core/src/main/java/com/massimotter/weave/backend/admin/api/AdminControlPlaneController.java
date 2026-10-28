package com.massimotter.weave.backend.admin.api;

import com.massimotter.weave.backend.api.error.ApiErrorResponse;
import com.massimotter.weave.backend.admin.api.AdminAuditEventResponse;
import com.massimotter.weave.backend.admin.api.AdminControlPlaneResponse;
import com.massimotter.weave.backend.admin.api.CapabilityWhitelistResponse;
import com.massimotter.weave.backend.admin.api.CapabilityWhitelistUpdateRequest;
import com.massimotter.weave.backend.capability.api.EffectivePolicyResponse;
import com.massimotter.weave.backend.capability.api.EffectivePolicySimulationRequest;
import com.massimotter.weave.backend.capability.api.EffectivePolicySimulationResponse;
import com.massimotter.weave.backend.admin.api.PlatformIdentityReadinessResponse;
import com.massimotter.weave.backend.admin.api.ProviderReadinessTestRequest;
import com.massimotter.weave.backend.admin.api.ProviderReadinessTestResponse;
import com.massimotter.weave.backend.admin.api.ProviderSelectionRequest;
import com.massimotter.weave.backend.admin.api.ProviderSelectionResponse;
import com.massimotter.weave.backend.admin.application.AdminControlPlaneService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.MediaType;
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
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "403", description = "Bearer token is missing the weave:workspace scope or effective workspace capability policy denies the operation.",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
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
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = AdminControlPlaneResponse.class)))
    public AdminControlPlaneResponse overview(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.overview(jwt);
    }

    @GetMapping("/api/admin/policies/capability-whitelist")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getCapabilityWhitelist", summary = "Read deny-by-default capability whitelist policy")
    @ApiResponse(responseCode = "200", description = "Capability whitelist snapshot.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = CapabilityWhitelistResponse.class)))
    public CapabilityWhitelistResponse whitelist(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.whitelist(jwt);
    }

    @GetMapping("/api/admin/policies/effective")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getEffectivePolicy", summary = "Explain the effective capability policy for the authenticated subject")
    @ApiResponse(responseCode = "200", description = "Support-safe effective policy explanation.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EffectivePolicyResponse.class)))
    public EffectivePolicyResponse effectivePolicy(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.effectivePolicy(jwt);
    }

    @PostMapping("/api/admin/policies/effective/simulations")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "simulateEffectivePolicy", summary = "Simulate effective capability policy before provider/realm changes")
    @ApiResponse(responseCode = "200", description = "Support-safe policy simulation.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = EffectivePolicySimulationResponse.class)))
    public EffectivePolicySimulationResponse simulateEffectivePolicy(
            @Valid @RequestBody EffectivePolicySimulationRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.simulateEffectivePolicy(request, jwt);
    }

    @GetMapping("/api/admin/platform/identity/readiness")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "getPlatformIdentityReadiness", summary = "Read support-safe fixed Keycloak platform readiness")
    @ApiResponse(responseCode = "200", description = "Backend-owned platform identity readiness.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = PlatformIdentityReadinessResponse.class)))
    public PlatformIdentityReadinessResponse platformIdentityReadiness(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.platformIdentityReadiness(jwt);
    }

    @PatchMapping("/api/admin/policies/capability-whitelist")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "updateCapabilityWhitelist", summary = "Reject edits to read-only capability profiles",
            description = "This compatibility endpoint preserves authorization and request validation, but does not change policy or publish a policy-update event.")
    @ApiResponses({
            @ApiResponse(responseCode = "400", description = "Invalid request or last-admin protection rejected the proposed profile.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Capability profiles are read-only; no policy was changed (capability-whitelist-read-only).",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public void updateWhitelist(
            @Valid @RequestBody CapabilityWhitelistUpdateRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        adminControlPlaneService.updateWhitelist(request, jwt);
    }

    @PostMapping("/api/admin/providers/selections")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "selectProvider", summary = "Record or dry-run category metadata without changing the active organization provider binding")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Support-safe category metadata result; not a provider cutover.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProviderSelectionResponse.class))),
            @ApiResponse(responseCode = "409", description = "Selection conflicts with the active binding or attempts unverified Files activation.",
                    content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ApiErrorResponse.class)))
    })
    public ProviderSelectionResponse selectProvider(
            @Valid @RequestBody ProviderSelectionRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.selectProvider(request, jwt);
    }

    @PostMapping("/api/admin/providers/readiness-tests")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "testProviderReadiness", summary = "Run a backend-owned support-safe provider readiness test contract")
    @ApiResponse(responseCode = "200", description = "Support-safe provider readiness test result.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ProviderReadinessTestResponse.class)))
    public ProviderReadinessTestResponse testProviderReadiness(
            @Valid @RequestBody ProviderReadinessTestRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.testProviderReadiness(request, jwt);
    }

    @GetMapping("/api/admin/audit/events")
    @PreAuthorize("hasAuthority('SCOPE_weave:workspace')")
    @Operation(operationId = "listAdminAuditEvents", summary = "Read support-safe admin/provider audit events")
    @ApiResponse(responseCode = "200", description = "Audit event list.",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    array = @ArraySchema(schema = @Schema(implementation = AdminAuditEventResponse.class))))
    public List<AdminAuditEventResponse> auditEvents(@AuthenticationPrincipal Jwt jwt) {
        return adminControlPlaneService.auditEvents(jwt);
    }
}
