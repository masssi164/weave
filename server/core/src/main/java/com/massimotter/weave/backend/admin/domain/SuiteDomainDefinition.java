package com.massimotter.weave.backend.admin.domain;

import java.util.List;

/** Existing Admin readiness metadata; actual product behavior belongs to domain use cases. */
public record SuiteDomainDefinition(
        String domain, String label, List<String> providerCategoryKeys,
        List<String> readCapabilities, List<String> writeCapabilities, List<String> canonicalObjectKinds) {
    public SuiteDomainDefinition {
        providerCategoryKeys = List.copyOf(providerCategoryKeys);
        readCapabilities = List.copyOf(readCapabilities);
        writeCapabilities = List.copyOf(writeCapabilities);
        canonicalObjectKinds = List.copyOf(canonicalObjectKinds);
    }

}
