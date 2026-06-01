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
        PortableExportImportContract portableExportImportContract,
        SwitchPlan switchPlan,
        ConsequencePreview consequencePreview,
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

    public record PortableExportImportContract(
            String exportManifestRef,
            String importManifestRef,
            String portabilityGuarantee,
            List<String> excludedAutomation,
            List<String> evidenceRefs) {
        public PortableExportImportContract {
            excludedAutomation = excludedAutomation == null ? List.of() : List.copyOf(excludedAutomation);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public record SwitchPlan(
            String planRef,
            boolean preflightRequired,
            boolean cutoverWindowRequired,
            boolean rollbackRequired,
            String memberFacingStateDuringSwitch,
            List<String> recoveryActions) {
        public SwitchPlan {
            recoveryActions = recoveryActions == null ? List.of() : List.copyOf(recoveryActions);
        }
    }

    public record ConsequencePreview(
            int preservedCount,
            int lossyCount,
            int unsupportedCount,
            int manualReviewCount,
            int archiveOnlyCount,
            List<String> memberImpactCopy,
            List<String> rollbackLimits,
            List<String> applyBlockers) {
        public ConsequencePreview {
            memberImpactCopy = memberImpactCopy == null ? List.of() : List.copyOf(memberImpactCopy);
            rollbackLimits = rollbackLimits == null ? List.of() : List.copyOf(rollbackLimits);
            applyBlockers = applyBlockers == null ? List.of() : List.copyOf(applyBlockers);
        }
    }
}
