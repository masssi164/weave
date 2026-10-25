package com.massimotter.weave.backend.model.migration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record MigrationApplyGateRequest(
        @NotBlank @Size(max = 128) String runId,
        @NotBlank @Size(max = 64) String domainKey,
        @NotBlank @Size(max = 64) String requestedLifecycle,
        @Size(max = 256) String dryRunReportRef,
        @Size(max = 256) String exportSnapshotRef,
        @Size(max = 256) String importPlanRef,
        @Size(max = 256) String providerMappingRef,
        @Size(max = 256) String lossyMappingReportRef,
        @Size(max = 256) String conflictReportRef,
        @Size(max = 256) String memberImpactPreviewRef,
        @Size(max = 256) String adminApprovalRef,
        @Size(max = 256) String rollbackArchiveRef,
        @Size(max = 256) String postApplyVerificationRef,
        @NotNull Map<@Size(max = 64) String, Integer> objectCounts,
        List<@Size(max = 96) String> contentHashes,
        List<@Size(max = 256) String> auditRefs,
        boolean identityMappingComplete,
        boolean auditSinkAvailable,
        boolean adminApproved,
        List<@Size(max = 512) String> providerDiagnostics) {

    public MigrationApplyGateRequest {
        objectCounts = objectCounts == null ? Map.of() : Map.copyOf(objectCounts);
        contentHashes = contentHashes == null ? List.of() : List.copyOf(contentHashes);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        providerDiagnostics = providerDiagnostics == null ? List.of() : List.copyOf(providerDiagnostics);
    }
}
