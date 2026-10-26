package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.api.ProviderCategoryContractResponse;
import com.massimotter.weave.backend.discovery.api.ProviderChoiceModelResponse;
import com.massimotter.weave.backend.discovery.provider.ProviderModule;
import com.massimotter.weave.backend.discovery.provider.ProviderRealityLevel;
import java.util.List;
import java.util.Set;

public final class ProviderCapabilityContracts {

    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");

    private final ProviderCategoryCatalog catalog;

    public ProviderCapabilityContracts(ProviderCategoryCatalog catalog) {
        this.catalog = java.util.Objects.requireNonNull(catalog, "catalog");
    }

    public ProviderCategoryContractResponse contract(String category, Set<ProviderModule> modules) {
        ProviderCategoryContract definition = catalog.statusCategory(category).contract();
        return new ProviderCategoryContractResponse(
                category,
                definition.featureCapabilities(),
                definition.defaultAdapters(),
                definition.externalAdapters(),
                definition.canonicalObjects(),
                definition.sourceOfTruth(),
                definition.lossyMappingRisks(),
                definition.exportDeleteExpectation(),
                definition.replacementRequirement(),
                choiceModels(definition.defaultAdapters(), definition.externalAdapters()),
                moduleNames(modules),
                STABLE_MEMBER_IMPACT_STATES,
                true,
                false);
    }

    public ProviderRealityLevel defaultRealityLevel(String category) {
        return catalog.statusCategory(category).defaultReality();
    }

    public List<String> providerCandidates(String category) {
        ProviderCategoryContract definition = catalog.statusCategory(category).contract();
        return java.util.stream.Stream.concat(definition.defaultAdapters().stream(), definition.externalAdapters().stream())
                .distinct()
                .sorted()
                .toList();
    }

    public List<String> canonicalObjects(String category) {
        return List.copyOf(catalog.statusCategory(category).contract().canonicalObjects());
    }

    public String sourceOfTruth(String category) {
        return catalog.statusCategory(category).contract().sourceOfTruth();
    }

    public List<String> lossyMappingRisks(String category) {
        return List.copyOf(catalog.statusCategory(category).contract().lossyMappingRisks());
    }

    public String exportDeleteExpectation(String category) {
        return catalog.statusCategory(category).contract().exportDeleteExpectation();
    }

    public String replacementRequirement(String category) {
        return catalog.statusCategory(category).contract().replacementRequirement();
    }

    private static List<ProviderChoiceModelResponse> choiceModels(
            List<String> defaultAdapters,
            List<String> externalAdapters) {
        return List.of(
                new ProviderChoiceModelResponse(
                        "recommended_self_hosted_default",
                        defaultAdapters,
                        List.of(
                                "recommended sovereign/default posture",
                                "admin still verifies backup, jurisdiction, lifecycle, and operator evidence"),
                        true),
                new ProviderChoiceModelResponse(
                        "external_existing_provider",
                        externalAdapters,
                        List.of(
                                "allowed when the organization already operates this provider category elsewhere",
                                "admin records tenant, data residency, retention, audit, and support boundary risk outside member UX"),
                        false),
                new ProviderChoiceModelResponse(
                        "managed_cloud_provider",
                        externalAdapters,
                        List.of(
                                "allowed as an interchangeable adapter posture, not a product boundary",
                                "admin must assess privacy, compliance, export, availability, and vendor lock-in risks"),
                        false),
                new ProviderChoiceModelResponse(
                        "hybrid_composite",
                        java.util.stream.Stream.concat(defaultAdapters.stream(), externalAdapters.stream()).distinct().sorted().toList(),
                        List.of(
                                "allowed when an organization mixes self-hosted, managed-cloud, and external providers across one capability boundary",
                                "admin must record source-of-truth, lossy mapping, support boundary, and replacement risk before member go-live"),
                        false));
    }

    private static List<String> moduleNames(Set<ProviderModule> modules) {
        return modules.stream()
                .map(ProviderModule::contractName)
                .sorted()
                .toList();
    }

}
