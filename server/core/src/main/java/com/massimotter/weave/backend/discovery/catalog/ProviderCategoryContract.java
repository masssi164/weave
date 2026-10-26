package com.massimotter.weave.backend.discovery.catalog;

import java.util.List;

/** Domain-owned semantic metadata, separate from shared readiness and authorization policy. */
public record ProviderCategoryContract(
        List<String> featureCapabilities,
        List<String> defaultAdapters,
        List<String> externalAdapters,
        List<String> canonicalObjects,
        String sourceOfTruth,
        List<String> lossyMappingRisks,
        String exportDeleteExpectation,
        String replacementRequirement) {
    public ProviderCategoryContract {
        featureCapabilities = List.copyOf(featureCapabilities);
        defaultAdapters = List.copyOf(defaultAdapters);
        externalAdapters = List.copyOf(externalAdapters);
        canonicalObjects = List.copyOf(canonicalObjects);
        lossyMappingRisks = List.copyOf(lossyMappingRisks);
        sourceOfTruth = requireText(sourceOfTruth);
        exportDeleteExpectation = requireText(exportDeleteExpectation);
        replacementRequirement = requireText(replacementRequirement);
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Category policy metadata is required");
        return value;
    }
}
