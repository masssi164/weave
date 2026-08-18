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
        NoUnaccountedDataLossReport noUnaccountedDataLossReport,
        BoundedApplyCutoverRollbackProof boundedProof,
        List<CrossDomainImpactItem> crossDomainImpact,
        BaselineSnapshot baselineSnapshot,
        ReadModelComparison readModelComparison,
        List<String> cutoverGates,
        List<String> memberImpactStates,
        List<String> evidenceRefs,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        List<String> auditRefs) {
    public ProviderReplacementDryRunResponse {
        crossDomainImpact = crossDomainImpact == null ? List.of() : List.copyOf(crossDomainImpact);
        cutoverGates = cutoverGates == null ? List.of() : List.copyOf(cutoverGates);
        memberImpactStates = memberImpactStates == null ? List.of() : List.copyOf(memberImpactStates);
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
    }

    public record BaselineSnapshot(
            String category,
            String persistedProviderKey,
            String persistedChoiceModel,
            String providerSelectionPersistencePosture,
            boolean persistedSelectionMatchesRequest,
            String profileOverridePersistencePosture,
            boolean profileOverridePresent,
            List<String> stableMemberImpactStates,
            List<String> evidenceRefs) {
        public BaselineSnapshot {
            stableMemberImpactStates = stableMemberImpactStates == null ? List.of() : List.copyOf(stableMemberImpactStates);
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }

    public record ReadModelComparison(
            boolean northboundContractUnchanged,
            boolean providerSemanticsLeakedToMembers,
            boolean memberImpactStatesProviderNeutral,
            boolean migrationEvidenceRecorded,
            List<String> findings) {
        public ReadModelComparison {
            findings = findings == null ? List.of() : List.copyOf(findings);
        }
    }

    @Schema(description = "Support-safe cross-domain provider impact item with canonical portability class.")
    public record CrossDomainImpactItem(
            String domainKey,
            String canonicalObjectRef,
            String mappingClass,
            String consequenceSummary,
            List<String> evidenceRefs,
            List<String> applyBlockers) {
        public CrossDomainImpactItem {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
            applyBlockers = applyBlockers == null ? List.of() : List.copyOf(applyBlockers);
        }
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

    public record NoUnaccountedDataLossReport(
            int supportedCount,
            int lossyCount,
            int unsupportedCount,
            int manualReviewCount,
            int archiveOnlyCount,
            int vendorLockedCount,
            List<String> knownLosses,
            List<String> unsupportedData,
            List<String> rollbackLimits,
            List<String> releaseClaimBoundaries) {
        public NoUnaccountedDataLossReport {
            knownLosses = knownLosses == null ? List.of() : List.copyOf(knownLosses);
            unsupportedData = unsupportedData == null ? List.of() : List.copyOf(unsupportedData);
            rollbackLimits = rollbackLimits == null ? List.of() : List.copyOf(rollbackLimits);
            releaseClaimBoundaries = releaseClaimBoundaries == null ? List.of() : List.copyOf(releaseClaimBoundaries);
        }
    }

    public record BoundedApplyCutoverRollbackProof(
            String proofBoundary,
            boolean limitedApplyAllowed,
            boolean productionCutoverAllowed,
            boolean rollbackRestoreSmokeRequired,
            List<String> requiredEvidenceRefs,
            List<String> releaseBlockers) {
        public BoundedApplyCutoverRollbackProof {
            requiredEvidenceRefs = requiredEvidenceRefs == null ? List.of() : List.copyOf(requiredEvidenceRefs);
            releaseBlockers = releaseBlockers == null ? List.of() : List.copyOf(releaseBlockers);
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
