package com.massimotter.weave.backend.model.migration;

import java.util.List;

public record MigrationDryRunResponse(
        String jobId,
        String status,
        String mode,
        String sourceProvider,
        InventorySummary inventory,
        MappingProposal mappingProposal,
        List<DomainMappingEvidence> domainMappings,
        List<ContinuityReport> continuityReports,
        UnmappableContentReport unmappableContent,
        ConsentRequirementReport consentRequirements,
        RateLimitBudgetEstimate rateLimitBudget,
        List<String> cutoverGates,
        boolean supportSafe,
        boolean providerDiagnosticsRedacted,
        boolean replaySafe,
        String reportDownloadPath) {

    public MigrationDryRunResponse {
        domainMappings = domainMappings == null ? List.of() : List.copyOf(domainMappings);
        continuityReports = continuityReports == null ? List.of() : List.copyOf(continuityReports);
        cutoverGates = cutoverGates == null ? List.of() : List.copyOf(cutoverGates);
    }

    public record InventorySummary(int workspaces, int channels, int users, int files, int messages) {
    }

    public record MappingProposal(int weaveRooms, int weaveMembers, int weaveGuests, List<String> assumptions) {
    }

    public record DomainMappingEvidence(
            String domain,
            String sourceObject,
            String weaveDomainObject,
            String targetAdapterObject,
            String mappingStatus,
            String mappingClass,
            List<String> lossyFields,
            List<String> conflicts,
            List<String> assumptions) {
        public DomainMappingEvidence {
            lossyFields = lossyFields == null ? List.of() : List.copyOf(lossyFields);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
            assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        }
    }

    public record ContinuityReport(
            String domain,
            java.util.Map<String, Integer> canonicalObjectCounts,
            String stableIdStrategy,
            List<String> provenanceRefs,
            List<String> lossyFields,
            List<String> permissionImpact,
            List<String> conflicts,
            List<String> unsupportedObjects,
            String abortRollbackPosture,
            boolean accountedForNoDataLoss) {
        public ContinuityReport {
            canonicalObjectCounts = canonicalObjectCounts == null ? java.util.Map.of() : java.util.Map.copyOf(canonicalObjectCounts);
            provenanceRefs = provenanceRefs == null ? List.of() : List.copyOf(provenanceRefs);
            lossyFields = lossyFields == null ? List.of() : List.copyOf(lossyFields);
            permissionImpact = permissionImpact == null ? List.of() : List.copyOf(permissionImpact);
            conflicts = conflicts == null ? List.of() : List.copyOf(conflicts);
            unsupportedObjects = unsupportedObjects == null ? List.of() : List.copyOf(unsupportedObjects);
        }
    }

    public record UnmappableContentReport(int count, List<String> reasons) {
    }

    public record ConsentRequirementReport(List<String> requiredScopes, List<String> missingScopes, boolean adminConsentRequired) {
    }

    public record RateLimitBudgetEstimate(int estimatedRequests, int roughDurationSeconds, List<String> degradedStates) {
    }
}
