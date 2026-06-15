package com.massimotter.weave.backend.provider;

import java.util.List;
import java.util.Set;

public record ProviderCategoryDefinition(
        String key,
        String label,
        Set<ProviderModule> modules,
        boolean capabilityBacked,
        List<String> defaultAdapters,
        List<String> externalAdapters) {
    public ProviderCategoryDefinition {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("provider category key is required");
        }
        if (label == null || label.isBlank()) {
            throw new IllegalArgumentException("provider category label is required");
        }
        modules = modules == null ? Set.of() : Set.copyOf(modules);
        defaultAdapters = defaultAdapters == null ? List.of() : List.copyOf(defaultAdapters);
        externalAdapters = externalAdapters == null ? List.of() : List.copyOf(externalAdapters);
    }

    public List<String> providerCandidates() {
        return java.util.stream.Stream.concat(defaultAdapters.stream(), externalAdapters.stream())
                .distinct()
                .sorted()
                .toList();
    }
}
