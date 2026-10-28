package com.massimotter.weave.backend.transfer.domain;

/** Stable provider-independent object identity used by transfer and reconciliation. */
public record CanonicalObjectId(String value) {
    public CanonicalObjectId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("canonical object id must not be blank");
        }
        value = value.trim();
    }
}
