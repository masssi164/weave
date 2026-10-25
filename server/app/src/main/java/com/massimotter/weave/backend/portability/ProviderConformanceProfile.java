package com.massimotter.weave.backend.portability;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record ProviderConformanceProfile(
        String domain,
        String adapterKey,
        Set<String> supportedOperations,
        Map<String, MappingClass> fieldMappings,
        boolean atomicWrites,
        boolean stableVersionTokens,
        boolean supportSafe) {

    public enum MappingClass {
        PORTABLE,
        LOSSY,
        UNSUPPORTED,
        MANUAL_REVIEW,
        VENDOR_LOCKED,
        ARCHIVE_ONLY
    }

    public ProviderConformanceProfile {
        domain = requireText(domain, "domain");
        adapterKey = requireText(adapterKey, "adapter key");
        supportedOperations = supportedOperations == null ? Set.of() : Set.copyOf(supportedOperations);
        fieldMappings = fieldMappings == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(fieldMappings));
    }

    public boolean supports(String operation) {
        return supportedOperations.contains(operation);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
