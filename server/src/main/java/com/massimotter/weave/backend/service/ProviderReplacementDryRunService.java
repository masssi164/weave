package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.model.admin.ProviderReplacementDryRunResponse;
import com.massimotter.weave.backend.provider.ProviderCapabilityContracts;
import com.massimotter.weave.backend.provider.ProviderCategoryCatalog;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ProviderReplacementDryRunService {

    private final ProviderSelectionService providerSelectionService;
    private final AuditEventPublisher auditEventPublisher;
    private final Clock clock;

    @Autowired
    public ProviderReplacementDryRunService(
            ProviderSelectionService providerSelectionService,
            AuditEventPublisher auditEventPublisher) {
        this(providerSelectionService, auditEventPublisher, Clock.systemUTC());
    }

    ProviderReplacementDryRunService(
            ProviderSelectionService providerSelectionService,
            AuditEventPublisher auditEventPublisher,
            Clock clock) {
        this.providerSelectionService = providerSelectionService;
        this.auditEventPublisher = auditEventPublisher;
        this.clock = clock;
    }

    public ProviderReplacementDryRunResponse dryRun(ProviderReplacementDryRunRequest request, String organizationId, String actorRef) {
        if (request == null || request.category() == null || request.category().isBlank()
                || request.currentAdapter() == null || request.currentAdapter().isBlank()
                || request.targetAdapter() == null || request.targetAdapter().isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-invalid",
                    "Provider replacement dry-run requires category, current adapter, and target adapter.",
                    Map.of("reason", "category/currentAdapter/targetAdapter are required"));
        }
        String category = request.category().trim();
        String currentAdapter = request.currentAdapter().trim();
        String targetAdapter = request.targetAdapter().trim();
        if (ProviderCategoryCatalog.category(category).isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-category-unknown",
                    "Provider category is not part of the Weave canonical control-plane contract.",
                    Map.of("category", category));
        }
        if (!providerSelectionService.providerMatchesCategory(currentAdapter, category) || !providerSelectionService.providerMatchesCategory(targetAdapter, category)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-category-mismatch",
                    "Provider replacement adapters must both be registered as support-safe candidates for the selected category.",
                    Map.of("category", category, "adapters", "unsupported-adapter-redacted"));
        }
        String choiceModel = providerSelectionChoiceModel(request.choiceModel());
        requiredSecretRef(request.secretRef());
        String declaredSourceOfTruth = safeSourceOfTruth(request.sourceOfTruth());
        List<String> adminNotes = safeLossyMappingNotes(request.lossyMappingNotes());
        boolean matrixChatDryRun = "chat".equals(category) && (isMatrixChatAdapter(currentAdapter) || isMatrixChatAdapter(targetAdapter));
        List<String> conflicts = new ArrayList<>();
        if (currentAdapter.equalsIgnoreCase(targetAdapter)) {
            conflicts.add("Current and target adapters are identical; record no-op or choose a distinct target before activation.");
        }
        if (matrixChatDryRun) {
            conflicts.add("Matrix Chat production apply/cutover remains blocked from Sprint 15 dry-run evidence; only the bounded Sprint 18 fixture apply/cutover/rollback proof may be reviewed.");
            conflicts.add("Encrypted room history requires a future client-side key/export strategy before any migration claim.");
            conflicts.add("Power-level parity and media retention stay manual-review blockers until operator evidence resolves them.");
        }
        boolean migrationRequired = true;
        String status = conflicts.isEmpty() ? "dry-run-ready" : matrixChatDryRun ? "dry-run-blocked-for-apply" : "requires-admin-review";
        String dryRunId = "provider-replacement-dry-run-" + category + "-" + Instant.now(clock).toEpochMilli();
        String auditRef = "provider-replacement-dry-run-" + category + "-" + Instant.now(clock).toEpochMilli();
        auditEventPublisher.publish(new AuditEvent(
                organizationId,
                "admin-control-plane",
                actorRef,
                "provider-replacement-dry-run",
                AuditAction.PROVIDER_REPLACEMENT_DRY_RUN,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.ofEntries(
                        Map.entry("category", category),
                        Map.entry("currentAdapter", currentAdapter),
                        Map.entry("targetAdapter", targetAdapter),
                        Map.entry("choiceModel", choiceModel),
                        Map.entry("sourceOfTruth", declaredSourceOfTruth),
                        Map.entry("secretRefPresent", true),
                        Map.entry("secretRef", safeSecretRef(request.secretRef())),
                        Map.entry("migrationDryRunRequired", migrationRequired),
                        Map.entry("portableExportImportRequired", request.portableExportImportRequired()),
                        Map.entry("lossyMappingNoteCount", adminNotes.size()),
                        Map.entry("rawProviderError", "redacted before audit"),
                        Map.entry("token", "not-stored"))));
        return new ProviderReplacementDryRunResponse(
                dryRunId,
                status,
                "dry-run",
                category,
                currentAdapter,
                targetAdapter,
                choiceModel,
                declaredSourceOfTruth,
                true,
                conflicts.isEmpty() ? "ready-for-admin-review" : "blocked-until-conflicts-resolved",
                migrationRequired,
                new ProviderReplacementDryRunResponse.LossyMappingReport(
                        ProviderCapabilityContracts.canonicalObjects(category),
                        ProviderCapabilityContracts.lossyMappingRisks(category),
                        adminNotes,
                        conflicts,
                        ProviderCapabilityContracts.replacementRequirement(category)),
                new ProviderReplacementDryRunResponse.LifecycleExpectations(
                        ProviderCapabilityContracts.sourceOfTruth(category),
                        ProviderCapabilityContracts.exportDeleteExpectation(category),
                        ProviderCapabilityContracts.exportDeleteExpectation(category),
                        "deprovision source identities, groups, memberships, grants, and service principals through the authoritative provider before capability cutover",
                        "rollback is an admin decision boundary; dry-run does not mutate provider state and apply must preserve mapping history"),
                new ProviderReplacementDryRunResponse.PortableExportImportContract(
                        category + "-portable-export-manifest-v0.1",
                        category + "-portable-import-manifest-v0.1",
                        "v0.1 guarantees a documented portable export/import contract before claiming automated migration.",
                        List.of("full automated cross-provider migration is not claimed in v0.1"),
                        List.of("provider-switch-preflight", "portable-export-import-contract", "rollback-recovery-plan", auditRef)),
                new ProviderReplacementDryRunResponse.SwitchPlan(
                        category + "-switch-plan-v0.1",
                        true,
                        true,
                        true,
                        matrixChatDryRun ? "coming_later" : "degraded",
                        matrixChatDryRun
                                ? List.of(
                                        "keep current Chat provider active; production cutover is not authorized by this proof",
                                        "retain source Matrix exports and rollback archive refs until media and permission-impact review is complete",
                                        "route member copy through provider-neutral states only")
                                : List.of(
                                        "keep current adapter active until export/import evidence is accepted",
                                        "block apply when rollback evidence or support-safe audit refs are missing")),
                consequencePreview(category, matrixChatDryRun, adminNotes, conflicts),
                noUnaccountedDataLossReport(category, matrixChatDryRun, adminNotes),
                boundedProof(category, matrixChatDryRun, auditRef),
                crossDomainImpact(category, matrixChatDryRun, auditRef),
                matrixChatDryRun
                        ? List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Backend Matrix Chat proof may only exercise bounded fixture apply/cutover/rollback evidence; production cutover remains blocked.",
                                "Resolve encrypted-room history, power-level impact, media retention, audit, rollback restore-smoke, and release-claim evidence before any future production gate.")
                        : List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Admin confirms source-of-truth, export/delete, lossy mapping, and rollback/support notes.",
                                "Readiness test and migration dry-run evidence are reviewed before activation."),
                matrixChatDryRun
                        ? List.of("available", "degraded", "unsupported", "coming_later")
                        : List.of("available", "disabled_by_policy", "degraded", "coming_later"),
                true,
                true,
                List.of(auditRef));
    }

    private List<ProviderReplacementDryRunResponse.CrossDomainImpactItem> crossDomainImpact(
            String category,
            boolean matrixChatDryRun,
            String auditRef) {
        if (!matrixChatDryRun) {
            return List.of(new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                    category,
                    "weave:" + category + ":provider-replacement-scope",
                    "manual_review",
                    "Backend dry-run must classify provider replacement impact before any apply or cutover claim.",
                    List.of(auditRef, category + "-portable-export-manifest-v0.1", category + "-portable-import-manifest-v0.1"),
                    List.of("cross-domain provider impact report is required before apply.")));
        }
        return List.of(
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "chat",
                        "weave:chat:conversation/sprint19-matrix-room",
                        "portable",
                        "Conversation metadata, current membership, simple replies, and canonical message refs are portable inside the bounded fixture.",
                        List.of("impact:s19:chat:matrix-room:portable", "specs/0006-portability-contract/matrix-synapse-chat-cross-domain-impact-proof.json"),
                        List.of()),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "files",
                        "weave:files:attachment-ref/sprint19-channel-media",
                        "archive_only",
                        "Matrix media references stay archive-only unless copied into Weave-controlled storage under an approved retention policy.",
                        List.of("impact:s19:files:attachment-retention", "docs/matrix-chat-migration-proof.md"),
                        List.of("media retention decision and rollback archive refs are required before cutover.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "boards",
                        "weave:boards:task-comment-link/sprint19-linked-decision",
                        "manual_review",
                        "Task/comment/watchers linked from Chat require manual review because Matrix sender roles do not map 1:1 to board permissions.",
                        List.of("impact:s19:boards:task-comment-watchers", "docs/matrix-chat-migration-proof.md"),
                        List.of("manual-review decision is required for board watcher and attachment relation impact.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "calendar",
                        "weave:calendar:event-link/sprint19-room-meeting",
                        "lossy",
                        "Meeting links and recurrence/resource metadata can be preserved only as support-safe refs when provider-specific room state has no canonical equivalent.",
                        List.of("impact:s19:calendar:meeting-link-recurrence", "docs/matrix-chat-migration-proof.md"),
                        List.of("calendar recurrence/resource lossy mapping must be accepted before cutover.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "decisions",
                        "weave:decisions:evidence-link/sprint19-chat-rationale",
                        "unsupported",
                        "Encrypted or redacted Chat rationale cannot be promoted into Decisions evidence by server-side migration and remains unsupported.",
                        List.of("impact:s19:decisions:encrypted-rationale", "docs/evidence/accessibility/sprint-18-manual-at-blocker.md"),
                        List.of("unsupported encrypted rationale blocks lossless migration and production replacement claims.")),
                new ProviderReplacementDryRunResponse.CrossDomainImpactItem(
                        "chat",
                        "weave:chat:provider-extension/sprint19-federated-widget",
                        "vendor_locked",
                        "Provider-specific widgets and federated extension state stay vendor-locked and cannot be represented as portable Weave domain data.",
                        List.of("impact:s19:chat:vendor-locked-widget", "specs/0006-portability-contract/matrix-synapse-chat-cross-domain-impact-proof.json"),
                        List.of("vendor-locked extension state blocks all-provider portability claims.")));
    }

    private ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport noUnaccountedDataLossReport(
            String category,
            boolean matrixChatDryRun,
            List<String> adminNotes) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport(
                    42,
                    7,
                    3,
                    5,
                    11,
                    0,
                    List.of("Complex relations and exact Matrix power-level parity are known lossy/manual-review areas."),
                    List.of("Encrypted Matrix history is unsupported for server-side migration without client-side key/export evidence."),
                    List.of(
                            "Rollback can clean bounded target imports and rely on retained source/archive refs.",
                            "Rollback cannot recreate unsupported encrypted history or exact Matrix power-level parity."),
                    List.of(
                            "This is one bounded Chat-domain Matrix/Synapse proof, not production migration availability.",
                            "No lossless migration, legal-compliance, E2EE-history, private-channel parity, or all-provider portability claim is made."));
        }
        return new ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport(
                Math.max(1, ProviderCapabilityContracts.canonicalObjects(category).size()),
                ProviderCapabilityContracts.lossyMappingRisks(category).size(),
                0,
                adminNotes.size(),
                0,
                0,
                ProviderCapabilityContracts.lossyMappingRisks(category),
                List.of(),
                List.of("Rollback boundary follows backend dry-run and archive evidence."),
                List.of("Provider replacement claims remain bounded by accepted dry-run evidence."));
    }

    private ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof boundedProof(
            String category,
            boolean matrixChatDryRun,
            String auditRef) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof(
                    "fixture_only_matrix_synapse_chat_sprint18",
                    true,
                    false,
                    true,
                    List.of(
                            category + "-portable-export-manifest-v0.1",
                            category + "-portable-import-manifest-v0.1",
                            category + "-cutover-plan-v0.1",
                            category + "-rollback-restore-smoke-v0.1",
                            category + "-no-unaccounted-data-loss-report-v0.1",
                            auditRef),
                    List.of(
                            "production provider mutation and cutover are blocked",
                            "manual-review Matrix power-level and media-retention decisions remain unresolved",
                            "encrypted history remains unsupported/coming_later"));
        }
        return new ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof(
                "dry_run_only",
                false,
                false,
                true,
                List.of(auditRef),
                List.of("bounded apply proof is not available for this provider category"));
    }

    private ProviderReplacementDryRunResponse.ConsequencePreview consequencePreview(
            String category,
            boolean matrixChatDryRun,
            List<String> adminNotes,
            List<String> conflicts) {
        if (matrixChatDryRun) {
            return new ProviderReplacementDryRunResponse.ConsequencePreview(
                    42,
                    7,
                    3,
                    5,
                    11,
                    List.of(
                            "Members keep Chat access during review; migration apply is coming_later and no provider internals are shown.",
                            "Encrypted history is unsupported for server migration until a client-side export strategy exists.",
                            "Some permissions and media require manual_review before any future cutover."),
                    List.of(
                            "Rollback depends on retained source Matrix export and support-safe archive refs.",
                            "Rollback cannot recreate unsupported encrypted history or exact Matrix power-level parity."),
                    List.copyOf(conflicts));
        }
        return new ProviderReplacementDryRunResponse.ConsequencePreview(
                Math.max(1, ProviderCapabilityContracts.canonicalObjects(category).size()),
                ProviderCapabilityContracts.lossyMappingRisks(category).size(),
                0,
                adminNotes.size(),
                0,
                List.of("Members see provider-neutral capability states while admins review replacement consequences."),
                List.of("Rollback boundary follows backend dry-run and archive evidence."),
                List.copyOf(conflicts));
    }

    private boolean isMatrixChatAdapter(String adapter) {
        String normalized = adapter == null ? "" : adapter.toLowerCase(Locale.ROOT);
        return normalized.contains("matrix") || normalized.contains("synapse");
    }
    private String requiredSecretRef(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-secretref-invalid",
                    "Provider replacement dry-run requires a backend SecretRef before activation can be evaluated.",
                    Map.of("secretRef", "invalid-secret-ref-redacted"));
        }
        return validateSecretRef(value, "provider-replacement-secretref-invalid", "Provider replacement dry-run may reference credentials only through SecretRef URIs.");
    }

    private String validateSecretRef(String value, String code, String message) {
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                code,
                message,
                Map.of("secretRef", "invalid-secret-ref-redacted"));
    }

    private String safeSourceOfTruth(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-source-of-truth-invalid",
                    "Provider replacement dry-run requires a support-safe source-of-truth declaration.",
                    Map.of("sourceOfTruth", "invalid-source-of-truth-redacted"));
        }
        String trimmed = value.trim();
        String redacted = safeText(trimmed);
        if (!redacted.equals(trimmed) || trimmed.length() > 160) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-source-of-truth-invalid",
                    "Provider replacement source-of-truth declaration must not contain URLs, bearer tokens, or secret material.",
                    Map.of("sourceOfTruth", "invalid-source-of-truth-redacted"));
        }
        return trimmed;
    }

    private String providerSelectionChoiceModel(String value) {
        try {
            return com.massimotter.weave.backend.provider.ProviderChoiceModel.normalize(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-selection-choice-model-invalid",
                    "Provider selection choice model is not part of the Weave provider choice contract.",
                    Map.of("choiceModel", "invalid-choice-model-redacted"));
        }
    }

    private List<String> safeLossyMappingNotes(List<String> notes) {
        if (notes == null) {
            return List.of();
        }
        return notes.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(this::safeText)
                .distinct()
                .limit(10)
                .toList();
    }

    private String safeSecretRef(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        String trimmed = value.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("secretref://")) {
            return trimmed;
        }
        return "invalid-secret-ref-redacted";
    }

    private String safeText(String value) {
        if (value == null || value.isBlank()) {
            return "not-provided";
        }
        return value.trim()
                .replaceAll("(?i)bearer\\s+[^\\s]+", "Bearer [redacted]")
                .replaceAll("(?i)xox[baprs]-[A-Za-z0-9-]+", "slack-token-[redacted]")
                .replaceAll("(?i)https?://[^\\s]+", "url-[redacted]")
                .replaceAll("(?i)secret(ref)?://[^\\s]+", "secret-ref-[redacted]");
    }

}
