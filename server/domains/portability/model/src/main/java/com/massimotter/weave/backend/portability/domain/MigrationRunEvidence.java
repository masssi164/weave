package com.massimotter.weave.backend.portability.domain;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MigrationRunEvidence(
        String runId,
        String domainKey,
        String lifecycle,
        Map<String, Integer> objectCounts,
        List<String> contentHashes,
        List<String> auditRefs,
        Map<String, String> artifactRefs,
        List<String> providerDiagnostics,
        boolean identityMappingComplete,
        boolean auditSinkAvailable,
        boolean adminApproved,
        Instant recordedAt,
        Instant expiresAt) {

    public MigrationRunEvidence {
        objectCounts = objectCounts == null ? Map.of() : Map.copyOf(objectCounts);
        contentHashes = contentHashes == null ? List.of() : List.copyOf(contentHashes);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        artifactRefs = artifactRefs == null ? Map.of() : Map.copyOf(artifactRefs);
        providerDiagnostics = providerDiagnostics == null ? List.of() : List.copyOf(providerDiagnostics);
    }

    public boolean expired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

}
