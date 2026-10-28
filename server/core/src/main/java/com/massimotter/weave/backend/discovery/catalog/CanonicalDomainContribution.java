package com.massimotter.weave.backend.discovery.catalog;

import com.massimotter.weave.backend.discovery.api.CanonicalDomainRegistryEntryResponse;
import java.util.Objects;

/** An installed owner's canonical contract declaration; this record does not implement a product feature. */
public record CanonicalDomainContribution(String owner, int order, CanonicalDomainRegistryEntryResponse entry) {
    public CanonicalDomainContribution {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Canonical owner is required");
        entry = Objects.requireNonNull(entry, "entry");
        if (entry.key() == null || entry.key().isBlank()) throw new IllegalArgumentException("Canonical key is required");
    }
}
