package com.massimotter.weave.backend.files.application;

/**
 * Canonical organization/space boundary for one Files mutation.
 *
 * <p>The provider binding revision remains explicit while the transitional canonical persistence
 * record still carries provider-binding state. It must not become a northbound identifier.</p>
 */
public record FilesCommandScope(
        String organizationRef,
        String spaceRef,
        long providerBindingRevision) {

    public FilesCommandScope {
        organizationRef = required(organizationRef, "organizationRef");
        spaceRef = required(spaceRef, "spaceRef");
        if (providerBindingRevision < 1) {
            throw new IllegalArgumentException("providerBindingRevision must be positive");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
