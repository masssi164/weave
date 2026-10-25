package com.massimotter.weave.backend.model.migration;

import java.util.List;
import java.util.Map;

public record MigrationApplyGateResponse(
        String runId,
        String domainKey,
        String lifecycle,
        boolean applyAllowed,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        List<String> requiredArtifacts,
        List<String> missingArtifacts,
        List<String> blockers,
        List<String> nextActions,
        SupportSafeEvidenceBundle evidenceBundle) {

    public MigrationApplyGateResponse {
        requiredArtifacts = requiredArtifacts == null ? List.of() : List.copyOf(requiredArtifacts);
        missingArtifacts = missingArtifacts == null ? List.of() : List.copyOf(missingArtifacts);
        blockers = blockers == null ? List.of() : List.copyOf(blockers);
        nextActions = nextActions == null ? List.of() : List.copyOf(nextActions);
    }

    public record SupportSafeEvidenceBundle(
            String runId,
            String domainKey,
            String lifecycle,
            Map<String, Integer> objectCounts,
            List<String> contentHashes,
            List<String> auditRefs,
            List<String> artifactRefs,
            List<String> redactedDiagnostics,
            String redaction) {

        public SupportSafeEvidenceBundle {
            objectCounts = objectCounts == null ? Map.of() : Map.copyOf(objectCounts);
            contentHashes = contentHashes == null ? List.of() : List.copyOf(contentHashes);
            auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
            artifactRefs = artifactRefs == null ? List.of() : List.copyOf(artifactRefs);
            redactedDiagnostics = redactedDiagnostics == null ? List.of() : List.copyOf(redactedDiagnostics);
        }
    }
}
