package com.massimotter.weave.backend.provider;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Provider-neutral capability contract for one admin Workspace Health category. Default and external adapters are implementations behind the contract, not member-facing product surfaces.")
public record ProviderCategoryContractResponse(
        @Schema(description = "Stable provider-neutral category key.") String category,
        @Schema(description = "Category-level feature capability keys enforced by Weave policy/facades.") List<String> featureCapabilities,
        @Schema(description = "Current self-hosted dogfood/default adapter keys for this category.") List<String> defaultAdapters,
        @Schema(description = "External or future adapter keys proving the category is not coupled to the dogfood default.") List<String> externalAdapters,
        @Schema(description = "Canonical Weave domain objects that adapters must map into without leaking provider schemas.") List<String> canonicalObjects,
        @Schema(description = "Admin-visible source-of-truth rule for this category.") String sourceOfTruth,
        @Schema(description = "Adapter fields or semantics that require explicit lossy mapping notes.") List<String> lossyMappingRisks,
        @Schema(description = "Export/delete/deprovision expectation used to avoid Weave becoming a silo.") String exportDeleteExpectation,
        @Schema(description = "Provider replacement or migration dry-run expectation.") String replacementRequirement,
        @Schema(description = "Risk-aware admin choice models for recommended self-hosted defaults, existing external providers, managed cloud providers, and hybrid composites.") List<ProviderChoiceModelResponse> choiceModels,
        @Schema(description = "Operational provider modules that can currently report readiness for this category.") List<String> adapterModules,
        @Schema(description = "Member-visible impact states that must remain stable even when an admin swaps adapters.") List<String> stableMemberImpactStates,
        @Schema(description = "Whether admins/operators may select or change the adapter for this category.") boolean adminSelectable,
        @Schema(description = "Always false: normal members never configure raw providers.") boolean normalMembersConfigureProviders) {

    public ProviderCategoryContractResponse {
        category = requireText(category, "category");
        featureCapabilities = featureCapabilities == null ? List.of() : List.copyOf(featureCapabilities);
        defaultAdapters = defaultAdapters == null ? List.of() : List.copyOf(defaultAdapters);
        externalAdapters = externalAdapters == null ? List.of() : List.copyOf(externalAdapters);
        canonicalObjects = canonicalObjects == null ? List.of() : List.copyOf(canonicalObjects);
        sourceOfTruth = sourceOfTruth == null || sourceOfTruth.isBlank() ? "admin-declared per organization" : sourceOfTruth.trim();
        lossyMappingRisks = lossyMappingRisks == null ? List.of() : List.copyOf(lossyMappingRisks);
        exportDeleteExpectation = exportDeleteExpectation == null || exportDeleteExpectation.isBlank() ? "export/delete behavior must be declared before provider activation" : exportDeleteExpectation.trim();
        replacementRequirement = replacementRequirement == null || replacementRequirement.isBlank() ? "preflight and dry-run required before adapter replacement" : replacementRequirement.trim();
        choiceModels = choiceModels == null ? List.of() : List.copyOf(choiceModels);
        adapterModules = adapterModules == null ? List.of() : List.copyOf(adapterModules);
        stableMemberImpactStates = stableMemberImpactStates == null ? List.of() : List.copyOf(stableMemberImpactStates);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
