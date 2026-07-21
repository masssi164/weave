package com.massimotter.weave.backend.providerbinding.domain;

import java.time.Instant;

/** Private southbound mapping. providerObjectRef must never be serialized to a member projection. */
public record ProviderObjectMapping(
        String organizationRef,
        String domain,
        long bindingRevision,
        String canonicalObjectId,
        String providerObjectRef,
        String provenance,
        Instant firstObservedAt,
        Instant lastObservedAt) {

    public ProviderObjectMapping {
        organizationRef = required(organizationRef, "organizationRef");
        domain = required(domain, "domain");
        canonicalObjectId = required(canonicalObjectId, "canonicalObjectId");
        providerObjectRef = required(providerObjectRef, "providerObjectRef");
        provenance = required(provenance, "provenance");
        firstObservedAt = java.util.Objects.requireNonNull(firstObservedAt, "firstObservedAt must not be null");
        lastObservedAt = java.util.Objects.requireNonNull(lastObservedAt, "lastObservedAt must not be null");
        if (bindingRevision < 1) {
            throw new IllegalArgumentException("bindingRevision must be positive");
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
