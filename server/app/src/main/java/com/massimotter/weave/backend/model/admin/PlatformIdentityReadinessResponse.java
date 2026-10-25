package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Backend-owned, support-safe Keycloak platform-security readiness facade for Workspace Health/Admin Console.")
public record PlatformIdentityReadinessResponse(
        String contractVersion,
        String platformAuthority,
        String overallState,
        boolean supportSafe,
        boolean diagnosticsRedacted,
        boolean backendOwnedFacade,
        boolean memberClientMayConfigurePlatformSecurity,
        boolean requiredForMemberFlows,
        Instant generatedAt,
        List<String> stableStates,
        List<PlatformIdentityReadinessCardResponse> cards,
        List<String> nextActions,
        Map<String, String> adminApiRoutes,
        Map<String, Object> diagnostics) {
    public PlatformIdentityReadinessResponse {
        contractVersion = contractVersion == null || contractVersion.isBlank()
                ? "platform-identity-readiness-v1"
                : contractVersion.trim();
        platformAuthority = platformAuthority == null || platformAuthority.isBlank()
                ? "keycloak"
                : platformAuthority.trim();
        overallState = normalizeState(overallState);
        supportSafe = supportSafe && diagnosticsRedacted && backendOwnedFacade
                && !memberClientMayConfigurePlatformSecurity;
        generatedAt = generatedAt == null ? Instant.EPOCH : generatedAt;
        stableStates = stableStates == null || stableStates.isEmpty()
                ? List.of("ready", "degraded", "policy-blocked", "admin-action-required", "coming_later", "disabled")
                : List.copyOf(stableStates.stream().map(PlatformIdentityReadinessResponse::normalizeState).distinct().toList());
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
            case "ready", "degraded", "policy-blocked", "admin-action-required", "coming-later", "disabled" -> normalized.equals("coming-later") ? "coming_later" : normalized;
            default -> "admin-action-required";
        };
    }
}
