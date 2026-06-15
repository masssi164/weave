package com.massimotter.weave.backend.model.admin;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Support-safe Admin/Operator attach-existing portability plan inspection response.")
public record AttachExistingPortabilityPlanResponse(
        String planId,
        String contractVersion,
        String mode,
        String domainKey,
        String status,
        String claimBoundary,
        boolean supportSafe,
        boolean adminOnlyProviderDetails,
        boolean destructiveActionAllowed,
        boolean providerMutationPerformed,
        boolean memberVisibleProviderInternals,
        List<CapabilityMapItem> capabilityMap,
        List<AdapterBinding> adapterBindings,
        String permissionImpactRef,
        List<ReportItem> permissionImpact,
        String lossReportRef,
        List<ReportItem> lossReport,
        String conflictReportRef,
        List<ReportItem> conflictReport,
        List<String> auditRefs,
        RecommendedTarget recommendedTarget,
        NextSteps nextSteps,
        List<String> memberCapabilityStates,
        NegativeChecks negativeChecks) {
    public AttachExistingPortabilityPlanResponse {
        capabilityMap = capabilityMap == null ? List.of() : List.copyOf(capabilityMap);
        adapterBindings = adapterBindings == null ? List.of() : List.copyOf(adapterBindings);
        permissionImpact = permissionImpact == null ? List.of() : List.copyOf(permissionImpact);
        lossReport = lossReport == null ? List.of() : List.copyOf(lossReport);
        conflictReport = conflictReport == null ? List.of() : List.copyOf(conflictReport);
        auditRefs = auditRefs == null ? List.of() : List.copyOf(auditRefs);
        memberCapabilityStates = memberCapabilityStates == null ? List.of() : List.copyOf(memberCapabilityStates);
    }

    public record CapabilityMapItem(
            String canonicalCapability,
            String sourceProviderCapability,
            String targetProviderCapability,
            String memberState) {
    }

    public record AdapterBinding(
            String adapterKey,
            List<String> domainKeys,
            String providerPosture,
            String bindingStatus,
            String discoveryMode,
            boolean activeBinding,
            boolean providerMutationPerformed,
            boolean memberVisibleProviderInternals,
            String auditRef) {
        public AdapterBinding {
            domainKeys = domainKeys == null ? List.of() : List.copyOf(domainKeys);
        }
    }

    public record ReportItem(
            String canonicalObject,
            String field,
            String fieldClass,
            String impact,
            String reason) {
    }

    public record RecommendedTarget(
            String providerKey,
            String reason) {
    }

    public record NextSteps(
            List<String> cutover,
            List<String> rollback) {
        public NextSteps {
            cutover = cutover == null ? List.of() : List.copyOf(cutover);
            rollback = rollback == null ? List.of() : List.copyOf(rollback);
        }
    }

    public record NegativeChecks(
            boolean noDestructiveActionInDiscoveryMode,
            boolean noMemberVisibleProviderInternals,
            boolean exactlyOneActiveBindingPerDomain) {
    }
}
