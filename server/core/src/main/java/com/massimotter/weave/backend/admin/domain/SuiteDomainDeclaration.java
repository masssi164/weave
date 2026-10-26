package com.massimotter.weave.backend.admin.domain;

import java.util.List;
import java.util.Objects;

/** One actual owner's fragment of a grouped Admin readiness presentation. */
public record SuiteDomainDeclaration(
        String owner, int order, int fragmentOrder, SuiteDomainDefinition definition,
        String sourceOfTruth, List<String> portabilityNotes, String nextAction) {
    public SuiteDomainDeclaration {
        if (owner == null || owner.isBlank()) throw new IllegalArgumentException("Suite owner is required");
        definition = Objects.requireNonNull(definition);
        portabilityNotes = List.copyOf(portabilityNotes);
    }
}
