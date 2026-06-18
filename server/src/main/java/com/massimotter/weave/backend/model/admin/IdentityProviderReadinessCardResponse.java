package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "One support-safe identity provider readiness card for Workspace Health/Admin Console.")
public record IdentityProviderReadinessCardResponse(
        @Schema(description = "Stable card key owned by the backend readiness facade.") String key,
        @Schema(description = "Accessibility-friendly card label.") String label,
        @Schema(description = "Stable support-safe state: ready, degraded, policy-blocked, admin-action-required, coming_later, or disabled.") String state,
        @Schema(description = "Support-safe operator summary with no provider internals or raw downstream payloads.") String summary,
        @Schema(description = "Member impact expressed as product-level state only.") String memberImpact,
        @Schema(description = "Immediate operator remediation copy.") String remediation,
        @Schema(description = "Concrete next operator/admin actions.") List<String> nextActions,
        @Schema(description = "Support-safe evidence refs/codes only; no URLs, client ids, realm internals, secrets, or raw provider errors.") List<String> evidenceRefs,
        @Schema(description = "Support-safe diagnostics booleans/counts/contract keys only.") Map<String, Object> diagnostics) {
    public IdentityProviderReadinessCardResponse {
        key = requireText(key, "key");
        label = requireText(label, "label");
        state = normalizeState(state);
        summary = requireText(summary, "summary");
        memberImpact = requireText(memberImpact, "memberImpact");
        remediation = requireText(remediation, "remediation");
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
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
