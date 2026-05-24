package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Schema(description = "Provider-neutral admin Workspace Health category readiness with support-safe diagnostics.")
public record ProviderCategoryStatusResponse(
        @Schema(description = "Stable provider-neutral category key.") String category,
        @Schema(description = "Human-readable provider-neutral category label.") String label,
        @Schema(description = "Admin readiness state for this category.") ProviderCategoryReadiness readiness,
        @Schema(description = "Effective capability policy state used for this category.") WorkspaceCapabilityPolicyState policyState,
        @Schema(description = "Member-safe impact label. Does not expose raw provider setup.") String memberImpact,
        @Schema(description = "Provider registry modules contributing to this category.") List<String> modules,
        @Schema(description = "Support-safe provider choices/candidates for admin diagnostics.") List<String> providerCandidates,
        @Schema(description = "Support-safe diagnostics. Values are booleans/counts/keys only; no endpoints, secrets, or raw upstream errors.") Map<String, Object> diagnostics) {

    public ProviderCategoryStatusResponse {
        category = requireText(category, "category");
        label = requireText(label, "label");
        readiness = readiness == null ? ProviderCategoryReadiness.DISABLED : readiness;
        policyState = policyState == null ? WorkspaceCapabilityPolicyState.UNAVAILABLE : policyState;
        memberImpact = requireText(memberImpact, "memberImpact");
        modules = modules == null ? List.of() : List.copyOf(modules);
        providerCandidates = providerCandidates == null ? List.of() : List.copyOf(providerCandidates);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
