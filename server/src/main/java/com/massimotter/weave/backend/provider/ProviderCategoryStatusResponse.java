package com.massimotter.weave.backend.provider;

import com.massimotter.weave.backend.model.WorkspaceCapabilityPolicyState;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Schema(description = "Provider-neutral admin Workspace Health category readiness with support-safe diagnostics.")
public record ProviderCategoryStatusResponse(
        @Schema(description = "Stable provider-neutral category key.") String category,
        @Schema(description = "Human-readable provider-neutral category label.") String label,
        @Schema(description = "Provider-neutral capability contract and adapter seam for this category.") ProviderCategoryContractResponse contract,
        @Schema(description = "Admin readiness state for this category.") ProviderCategoryReadiness readiness,
        @Schema(description = "Evidence-backed provider implementation maturity for this category.") ProviderRealityLevel providerRealityLevel,
        @Schema(description = "Stable member capability state derived from policy, readiness, and provider reality level.") String memberCapabilityState,
        @Schema(description = "Actionable admin remediation for the current provider reality level.") String realityLevelRemediation,
        @Schema(description = "Effective capability policy state used for this category.") WorkspaceCapabilityPolicyState policyState,
        @Schema(description = "Member-safe impact label. Does not expose raw provider setup.") String memberImpact,
        @Schema(description = "Provider registry modules contributing to this category.") List<String> modules,
        @Schema(description = "Support-safe provider choices/candidates for admin diagnostics.") List<String> providerCandidates,
        @Schema(description = "Admin Console-selected provider key, or awaiting_admin_selection when not applied.") String selectedProviderKey,
        @Schema(description = "Selected provider choice model: recommended_self_hosted_default, external_existing_provider, or managed_cloud_provider.") String choiceModel,
        @Schema(description = "True only after an admin has applied a provider mapping for this category.") boolean selectedByAdmin,
        @Schema(description = "True when defaults are being shown only as bootstrap/profile suggestions, not product truth.") boolean bootstrapSuggestionOnly,
        @Schema(description = "Support-safe notes for known lossy mappings across provider families.") List<String> lossyMappingNotes,
        @Schema(description = "Support-safe infra/backend adapter readiness evidence for Admin Console and support bundles.") List<ProviderAdapterReadinessEvidenceResponse> adapterEvidence,
        @Schema(description = "Support-safe diagnostics. Values are booleans/counts/keys only; no endpoints, secrets, or raw upstream errors.") Map<String, Object> diagnostics) {

    public ProviderCategoryStatusResponse {
        category = requireText(category, "category");
        label = requireText(label, "label");
        contract = contract == null
                ? ProviderCapabilityContracts.contract(category, Set.of())
                : contract;
        readiness = readiness == null ? ProviderCategoryReadiness.DISABLED : readiness;
        providerRealityLevel = providerRealityLevel == null ? ProviderRealityLevel.CONTRACT_ONLY : providerRealityLevel;
        memberCapabilityState = requireText(memberCapabilityState == null ? "unavailable" : memberCapabilityState, "memberCapabilityState");
        realityLevelRemediation = requireText(realityLevelRemediation == null ? "Admin review is required before member availability." : realityLevelRemediation, "realityLevelRemediation");
        policyState = policyState == null ? WorkspaceCapabilityPolicyState.UNAVAILABLE : policyState;
        memberImpact = requireText(memberImpact, "memberImpact");
        modules = modules == null ? List.of() : List.copyOf(modules);
        providerCandidates = providerCandidates == null ? List.of() : List.copyOf(providerCandidates);
        selectedProviderKey = selectedProviderKey == null || selectedProviderKey.isBlank() ? "awaiting_admin_selection" : selectedProviderKey.trim();
        choiceModel = choiceModel == null || choiceModel.isBlank() ? "not_selected" : choiceModel.trim();
        lossyMappingNotes = lossyMappingNotes == null ? List.of() : List.copyOf(lossyMappingNotes);
        adapterEvidence = adapterEvidence == null ? List.of() : List.copyOf(adapterEvidence);
        diagnostics = diagnostics == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(diagnostics));
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
