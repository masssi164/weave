package com.massimotter.weave.contract.domain;

import java.util.List;

public record ProductDomainContract(
        String key,
        String label,
        List<String> canonicalObjectKinds,
        List<String> readCapabilities,
        List<String> writeCapabilities,
        List<String> providerAdapterSlots,
        List<String> providerCategoryHints,
        List<String> portabilityRequirements) {

    public ProductDomainContract {
        key = text(key, "key");
        label = text(label, "label");
        canonicalObjectKinds = List.copyOf(canonicalObjectKinds == null ? List.of() : canonicalObjectKinds);
        readCapabilities = List.copyOf(readCapabilities == null ? List.of() : readCapabilities);
        writeCapabilities = List.copyOf(writeCapabilities == null ? List.of() : writeCapabilities);
        providerAdapterSlots = List.copyOf(providerAdapterSlots == null ? List.of() : providerAdapterSlots);
        providerCategoryHints = List.copyOf(providerCategoryHints == null ? List.of() : providerCategoryHints);
        portabilityRequirements = List.copyOf(portabilityRequirements == null ? List.of() : portabilityRequirements);
    }

    public List<String> allCapabilities() {
        return java.util.stream.Stream.concat(readCapabilities.stream(), writeCapabilities.stream())
                .collect(java.util.stream.Collectors.toUnmodifiableList());
    }

    private static String text(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must not be blank");
        return value.trim();
    }
}
