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
                        List.of("Chat channels map to canonical Weave Chat conversations.", "Files map to canonical Weave Files objects before target-adapter import.", "Unmatched external users become guests only with explicit policy.")),
                domainMappings(request.sourceProvider(), inventory, unmappable),
                new MigrationDryRunResponse.UnmappableContentReport(
                        unmappable,
                        unmappable == 0 ? List.of() : List.of("External users without workspace member mapping require guest policy.")),
                new MigrationDryRunResponse.ConsentRequirementReport(requiredScopes, missing, !missing.isEmpty()),
                new MigrationDryRunResponse.RateLimitBudgetEstimate(
                        estimatedRequests,
                        estimatedRequests * 2,
                        List.of("rate_limited", "retry_after", "quota_exhausted")),
                List.of(
                        "Admin reviews lossy/unmappable evidence before any apply phase.",
                        "Capability, IDM identity mapping, export/import scopes, and rollback marker must be ready.",
                        "Member clients continue to consume Weave domain DTOs; provider internals remain admin-only."),
                true,
                true,
                true,
                "/api/migration/dry-runs/" + jobId + "/report");
    }

    private List<MigrationDryRunResponse.DomainMappingEvidence> domainMappings(
            String sourceProvider,
            MigrationDryRunRequest.SourceInventory inventory,
            int unmappableUsers) {
        String provider = sourceProvider == null ? "external-provider" : sourceProvider.toLowerCase();
        return List.of(
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "chat",
                        provider + ":channels/messages/memberships",
                        "weave:chat:conversations/messages/memberships/history-policy/attachment-refs",
                        "target-adapter:chat:conversations/messages/memberships",
                        unmappableUsers > 0 ? "requires-admin-review" : "mappable",
                        inventory.messages() > 0
                                ? List.of("provider-specific reactions, pins, bot metadata, thread semantics, and encrypted/redacted history may be lossy")
                                : List.of(),
                        unmappableUsers > 0
                                ? List.of("identity conflicts must resolve against IDM/RBAC mapping before cutover")
                                : List.of(),
                        List.of(
                                "conversation ids are canonicalized before target import",
                                "attachments re-link through Weave Files/attachment facades; raw media URLs are redacted")),
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "files",
                        provider + ":files/folders/shares/versions",
                        "weave:files:paths/folders/versions/shares/owners",
                        "target-adapter:files:objects/shares/versions",
                        inventory.files() > 0 ? "mappable-with-review" : "no-source-objects",
                        inventory.files() > 0
                                ? List.of("unsupported metadata, external links, missing versions, quota/rate limits may be lossy")
                                : List.of(),
                        List.of(),
                        List.of(
                                "path conflicts receive deterministic conflict suffixes during dry-run evidence",
                                "ACL and ownership imports require IDM identity mapping and admin consent scopes")));
    }

    private List<String> requiredScopes(String provider) {
        return switch (provider == null ? "" : provider.toLowerCase()) {
            case "slack" -> List.of("channels:read", "users:read", "files:read");
            case "teams" -> List.of("Channel.ReadBasic.All", "User.Read.All", "Files.Read.All");
            default -> List.of("inventory:read");
        };
    }
}
