package com.massimotter.weave.backend.model.migration;

import java.util.List;

public record MigrationDryRunResponse(
        String jobId,
        String status,
        String mode,
        String sourceProvider,
        InventorySummary inventory,
        MappingProposal mappingProposal,
        UnmappableContentReport unmappableContent,
        ConsentRequirementReport consentRequirements,
        RateLimitBudgetEstimate rateLimitBudget,
        boolean replaySafe,
        String reportDownloadPath) {

    public record InventorySummary(int workspaces, int channels, int users, int files, int messages) {
    }

    public record MappingProposal(int weaveRooms, int weaveMembers, int weaveGuests, List<String> assumptions) {
    }

    public record UnmappableContentReport(int count, List<String> reasons) {
    }

    public record ConsentRequirementReport(List<String> requiredScopes, List<String> missingScopes, boolean adminConsentRequired) {
    }

    public record RateLimitBudgetEstimate(int estimatedRequests, int roughDurationSeconds, List<String> degradedStates) {
    }
}
