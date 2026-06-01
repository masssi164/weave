package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationDryRunRequest;
import com.massimotter.weave.backend.model.migration.MigrationDryRunResponse;
import com.massimotter.weave.backend.service.interop.IdempotencyKeyService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MigrationDryRunService {

    private static final Duration EVIDENCE_TTL = Duration.ofHours(24);

    private final IdempotencyKeyService idempotencyKeyService;
    private final MigrationRunEvidenceRepository evidenceRepository;

    public MigrationDryRunService(IdempotencyKeyService idempotencyKeyService,
            MigrationRunEvidenceRepository evidenceRepository) {
        this.idempotencyKeyService = idempotencyKeyService;
        this.evidenceRepository = evidenceRepository;
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
        boolean matrixChatDryRun = isMatrixChatProvider(request.sourceProvider());
        var response = new MigrationDryRunResponse(
                jobId,
                "completed",
                "dry-run",
                normalizeProvider(request.sourceProvider()),
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
                matrixChatDryRun
                        ? List.of(
                                "Sprint 15 Matrix Chat dry-run evidence is review-only; apply/cutover remains blocked by default.",
                                "Encrypted-room history is unsupported until client-side key/export strategy evidence exists.",
                                "Power-level parity, media retention, audit refs, and rollback archive refs require admin review before any later apply gate.",
                                "Member clients continue to consume Weave domain DTOs; provider internals remain admin-only.")
                        : List.of(
                                "Admin reviews lossy/unmappable evidence before any apply phase.",
                                "Capability, IDM identity mapping, export/import scopes, and rollback marker must be ready.",
                                "Member clients continue to consume Weave domain DTOs; provider internals remain admin-only."),
                true,
                true,
                true,
                "/api/migration/dry-runs/" + jobId + "/report");
        persistServerEvidence(response);
        return response;
    }

    private List<MigrationDryRunResponse.DomainMappingEvidence> domainMappings(
            String sourceProvider,
            MigrationDryRunRequest.SourceInventory inventory,
            int unmappableUsers) {
        String provider = normalizeProvider(sourceProvider);
        return List.of(
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "files",
                        provider + ":files/folders/shares/versions",
                        "weave:files:paths/folders/versions/shares/owners",
                        "target-adapter:files:objects/shares/versions",
                        inventory.files() > 0 ? "manual_review_required" : "no-source-objects",
                        inventory.files() > 0
                                ? List.of("unsupported metadata, external links, missing versions, quota/rate limits may be lossy")
                                : List.of(),
                        List.of("path or ownership conflicts block apply until resolved"),
                        List.of(
                                "path conflicts receive deterministic conflict suffixes during dry-run evidence",
                                "ACL and ownership imports require IDM identity mapping and admin consent scopes")),
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "calendar",
                        provider + ":calendars/events/organizers/resources",
                        "weave:calendar:calendars/events/organizers/resources/recurrence",
                        "target-adapter:calendar:calendars/events/participants/resources",
                        "manual_review_required",
                        List.of("provider-specific recurrence exceptions, alarms, room resources, and attachment links may be lossy"),
                        List.of("organizer and resource ownership consequences require admin review"),
                        List.of("member previews show stable calendar impact states, never raw provider event urls")),
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "boards",
                        provider + ":boards/lists/cards/labels/watchers",
                        "weave:boards:projects/boards/columns/tasks/labels/watchers",
                        "target-adapter:boards:projects/boards/tasks",
                        "manual_review_required",
                        List.of("automation rules, custom fields, comments, and watchers may be unsupported or archive-only"),
                        List.of("permission and ownership drift blocks apply until transfer evidence exists"),
                        List.of("unsupported provider actions stay admin-visible and are not surfaced as member internals")),
                new MigrationDryRunResponse.DomainMappingEvidence(
                        "chat",
                        provider + ":channels/messages/memberships/e2ee-state",
                        "weave:chat:conversations/messages/memberships/history-policy/attachment-refs",
                        "target-adapter:chat:conversations/messages/memberships",
                        unmappableUsers > 0 ? "manual_review_required" : "mappable",
                        inventory.messages() > 0
                                ? List.of("provider-specific reactions, pins, bot metadata, thread semantics, and encrypted/redacted history may be lossy or archive-only")
                                : List.of(),
                        unmappableUsers > 0
                                ? List.of("identity conflicts must resolve against IDM/RBAC mapping before cutover")
                                : List.of(),
                        List.of(
                                "conversation ids are canonicalized before target import",
                                "attachments re-link through Weave Files/attachment facades; raw media URLs are redacted")));
    }

    private void persistServerEvidence(MigrationDryRunResponse response) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(EVIDENCE_TTL);
        for (MigrationDryRunResponse.DomainMappingEvidence mapping : response.domainMappings()) {
            String domain = mapping.domain();
            evidenceRepository.save(new MigrationRunEvidence(
                    response.jobId(),
                    domain,
                    "dry_run_completed",
                    objectCountsFor(response.inventory(), domain),
                    List.of(contentHash(response.jobId() + ":" + domain + ":" + mapping.mappingStatus())),
                    List.of("audit:migration.dry_run:" + response.jobId() + ":" + domain),
                    artifactRefs(response.jobId(), domain),
                    List.of("support-safe migration dry-run evidence"),
                    response.consentRequirements().missingScopes().isEmpty(),
                    true,
                    false,
                    now,
                    expiresAt));
        }
    }

    private Map<String, Integer> objectCountsFor(MigrationDryRunResponse.InventorySummary inventory, String domain) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        if ("chat".equals(domain)) {
            counts.put("Workspace", inventory.workspaces());
            counts.put("Conversation", inventory.channels());
            counts.put("Member", inventory.users());
            counts.put("Message", inventory.messages());
        } else if ("files".equals(domain)) {
            counts.put("File", inventory.files());
            counts.put("Workspace", inventory.workspaces());
        } else if ("calendar".equals(domain)) {
            counts.put("Calendar", Math.max(1, inventory.workspaces()));
            counts.put("Event", Math.max(1, inventory.channels()));
            counts.put("Participant", inventory.users());
        } else if ("boards".equals(domain)) {
            counts.put("Board", Math.max(1, inventory.workspaces()));
            counts.put("Task", Math.max(1, inventory.channels() + inventory.files()));
            counts.put("Watcher", inventory.users());
        } else {
            counts.put("Object", Math.max(1, inventory.workspaces() + inventory.channels() + inventory.users() + inventory.files() + inventory.messages()));
        }
        return counts;
    }

    private Map<String, String> artifactRefs(String jobId, String domain) {
        String prefix = "migration:" + jobId + ":" + domain + ":";
        Map<String, String> refs = new LinkedHashMap<>();
        refs.put("dryRunReportRef", prefix + "dry-run-report");
        refs.put("exportSnapshotRef", prefix + "export-snapshot");
        refs.put("importPlanRef", prefix + "import-plan");
        refs.put("providerMappingRef", prefix + "provider-mapping");
        refs.put("lossyMappingReportRef", prefix + "lossy-mapping-report");
        refs.put("conflictReportRef", prefix + "conflict-report");
        refs.put("memberImpactPreviewRef", prefix + "member-impact-preview");
        refs.put("cutoverPlanRef", prefix + "cutover-plan");
        refs.put("rollbackArchiveRef", prefix + "rollback-archive");
        refs.put("rollbackRestoreSmokeRef", prefix + "rollback-restore-smoke");
        refs.put("noUnaccountedDataLossReportRef", prefix + "no-unaccounted-data-loss-report");
        refs.put("releaseClaimBoundaryRef", prefix + "release-claim-boundary");
        refs.put("postApplyVerificationRef", prefix + "post-apply-verification");
        return refs;
    }

    private String contentHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest is required for migration evidence", e);
        }
    }

    private List<String> requiredScopes(String provider) {
        return switch (normalizeProvider(provider)) {
            case "slack" -> List.of("channels:read", "users:read", "files:read");
            case "teams" -> List.of("Channel.ReadBasic.All", "User.Read.All", "Files.Read.All");
            case "matrix-synapse", "matrix-synapse-chat", "synapse-homeserver" -> List.of("rooms:read", "members:read", "messages:read", "media:read");
            default -> List.of("inventory:read");
        };
    }

    private boolean isMatrixChatProvider(String provider) {
        String normalized = normalizeProvider(provider);
        return normalized.contains("matrix") || normalized.contains("synapse");
    }

    private String normalizeProvider(String provider) {
        return provider == null ? "external-provider" : provider.toLowerCase(Locale.ROOT);
    }
}
