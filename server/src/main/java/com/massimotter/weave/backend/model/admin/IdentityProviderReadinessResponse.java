package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Backend-owned, support-safe identity provider readiness facade for Workspace Health/Admin Console.")
public record IdentityProviderReadinessResponse(
        String contractVersion,
        String category,
        String providerKey,
        String overallState,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        boolean backendOwnedFacade,
        boolean memberClientMayConfigureIdentityProvider,
        boolean optionalForMemberFlows,
        Instant generatedAt,
        List<String> stableStates,
        List<IdentityProviderReadinessCardResponse> cards,
        List<String> nextActions,
        Map<String, String> adminApiRoutes,
        Map<String, Object> diagnostics) {
    public IdentityProviderReadinessResponse {
        contractVersion = contractVersion == null || contractVersion.isBlank()
                ? "identity-provider-readiness-v1"
                : contractVersion.trim();
        category = category == null || category.isBlank() ? "identity-idm" : category.trim();
        providerKey = providerKey == null || providerKey.isBlank() ? "awaiting_admin_selection" : providerKey.trim();
        overallState = normalizeState(overallState);
        supportSafe = supportSafe && providerDiagnosticsRedacted && backendOwnedFacade && !memberClientMayConfigureIdentityProvider;
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
        stableStates = stableStates == null || stableStates.isEmpty()
                ? List.of("ready", "degraded", "policy-blocked", "admin-action-required", "disabled")
                : List.copyOf(stableStates.stream().map(IdentityProviderReadinessResponse::normalizeState).distinct().toList());
        cards = cards == null ? List.of() : List.copyOf(cards);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        adminApiRoutes = adminApiRoutes == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(adminApiRoutes));
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    private static String normalizeState(String value) {
        if (value == null || value.isBlank()) {
            return "admin-action-required";
        }
        String normalized = value.trim().replace('_', '-');
        return switch (normalized) {
            case "ready", "degraded", "policy-blocked", "admin-action-required", "disabled" -> normalized;
            default -> "admin-action-required";
        };
    }
}
