package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe provider replacement dry-run report for Admin Console review.")
public record ProviderReplacementDryRunResponse(
        String dryRunId,
        String status,
        String mode,
        String category,
        String currentAdapter,
        String targetAdapter,
        String choiceModel,
        String declaredSourceOfTruth,
        boolean secretRefPresent,
        String readinessState,
        boolean migrationDryRunRequired,
        LossyMappingReport lossyMappingReport,
        LifecycleExpectations lifecycleExpectations,
        List<String> cutoverGates,
        List<String> memberImpactStates,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        List<String> auditRefs) {
    public ProviderReplacementDryRunResponse {
        cutoverGates = cutoverGates == null ? List.of() : List.copyOf(cutoverGates);
        memberImpactStates = memberImpactStates == null ? List.of() : List.copyOf(memberImpactStates);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }

    public record LossyMappingReport(
            List<String> canonicalObjects,
            List<String> contractRisks,
            List<String> adminNotes,
            List<String> conflicts,
            String replacementRequirement) {
        public LossyMappingReport {
            canonicalObjects = canonicalObjects == null ? List.of() : List.copyOf(canonicalObjects);
            contractRisks = contractRisks == null ? List.of() : List.copyOf(contractRisks);
            adminNotes = adminNotes == null ? List.of() : List.copyOf(adminNotes);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
        }
    }

    public record LifecycleExpectations(
            String sourceOfTruthPolicy,
            String exportExpectation,
            String deleteExpectation,
            String deprovisionExpectation,
            String rollbackSupportBoundary) {
    }
}
