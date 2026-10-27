package com.massimotter.weave.backend.files.application;

/** Canonical organization/space boundary for one Files command or query. */
public record FilesScope(String organizationRef, String spaceRef) {

    public FilesScope {
        organizationRef = required(organizationRef, "organizationRef");
        spaceRef = required(spaceRef, "spaceRef");
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
