package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.interop.IdempotencyKeyService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class MigrationDryRunService {

    private final IdempotencyKeyService idempotencyKeyService;

    public MigrationDryRunService(IdempotencyKeyService idempotencyKeyService) {
        this.idempotencyKeyService = idempotencyKeyService;
    }

    public MigrationDryRunResponse dryRun(MigrationDryRunRequest request) {
        MigrationDryRunRequest.SourceInventory inventory = request.inventory();
        List<String> scopes = inventory.scopes() == null ? List.of() : inventory.scopes();
        List<String> requiredScopes = requiredScopes(request.sourceProvider());
        List<String> missing = requiredScopes.stream().filter(scope -> !scopes.contains(scope)).toList();
        int estimatedRequests = Math.max(1,
                inventory.workspaces() + inventory.channels() + inventory.users()
                        + ((inventory.files() + 99) / 100) + ((inventory.messages() + 199) / 200));
        int unmappable = Math.max(0, inventory.users() - inventory.channels() - inventory.workspaces());
        String stable = request.sourceProvider() + ":" + inventory.workspaces() + ":" + inventory.channels() + ":"
                + inventory.users() + ":" + inventory.files() + ":" + inventory.messages() + ":" + String.join(",", scopes);
        String jobId = idempotencyKeyService.key("migration:dry-run", stable);
        return new MigrationDryRunResponse(
                jobId,
                "completed",
                "dry-run",
                request.sourceProvider().toLowerCase(),
                new MigrationDryRunResponse.InventorySummary(
                        inventory.workspaces(), inventory.channels(), inventory.users(), inventory.files(), inventory.messages()),
                new MigrationDryRunResponse.MappingProposal(
                        inventory.channels(),
                        Math.max(0, inventory.users() - unmappable),
                        unmappable,
                        List.of("Channels map to Weave rooms.", "Unmatched external users become guests only with explicit policy.")),
                new MigrationDryRunResponse.UnmappableContentReport(
                        unmappable,
                        unmappable == 0 ? List.of() : List.of("External users without workspace member mapping require guest policy.")),
                new MigrationDryRunResponse.ConsentRequirementReport(requiredScopes, missing, !missing.isEmpty()),
                new MigrationDryRunResponse.RateLimitBudgetEstimate(
                        estimatedRequests,
                        estimatedRequests * 2,
                        List.of("rate_limited", "retry_after", "quota_exhausted")),
                true,
                "/api/migration/dry-runs/" + jobId + "/report");
    }

    private List<String> requiredScopes(String provider) {
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "slack" -> List.of("channels:read", "users:read", "files:read");
            case "teams" -> List.of("Channel.ReadBasic.All", "User.Read.All", "Files.Read.All");
            default -> List.of("inventory:read");
        };
    }
}
