package com.massimotter.weave.backend.portability.application;

import com.massimotter.weave.backend.capability.WorkspaceCapabilityService;
import com.massimotter.weave.backend.identity.profile.domain.ProductProfileOverride;
import com.massimotter.weave.backend.identity.profile.port.ProductProfileOverrideRepository;
import com.massimotter.weave.backend.identity.OrganizationIdentityContextResolver;
import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.api.error.ApiErrorException;
import com.massimotter.weave.backend.discovery.catalog.ProviderCapabilityContracts;
import com.massimotter.weave.backend.discovery.catalog.ProviderCategoryCatalog;
import com.massimotter.weave.backend.discovery.application.ProviderRegistry;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelection;
import com.massimotter.weave.backend.providerbinding.selection.ProviderSelectionRepository;
import com.massimotter.weave.backend.discovery.provider.ProviderStatusResponse;
import com.massimotter.weave.backend.portability.domain.MigrationRunEvidence;
import com.massimotter.weave.backend.portability.port.MigrationRunEvidenceRepository;
import com.massimotter.weave.backend.portability.api.ProviderReplacementDryRunRequest;
import com.massimotter.weave.backend.portability.api.ProviderReplacementDryRunResponse;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;

/** Existing future provider-replacement planning; never authorizes unverified production cutover. */
public class ProviderReplacementDryRunService {
    private static final List<String> STABLE_MEMBER_IMPACT_STATES = List.of(
            "available",
            "disabled_by_policy",
            "not_configured",
            "degraded",
            "unavailable",
            "coming_later");
    private static final Duration PROVIDER_REPLACEMENT_EVIDENCE_TTL = Duration.ofHours(24);
    private final ProviderCategoryCatalog providerCategoryCatalog;
    private final ProviderCapabilityContracts providerCapabilityContracts;
    private final ProviderRegistry providerRegistry;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final ProviderSelectionRepository providerSelectionRepository;
    private final ProductProfileOverrideRepository productProfileOverrideRepository;
    private final AuditEventPublisher auditEventPublisher;
    private final MigrationRunEvidenceRepository migrationRunEvidenceRepository;
    private final OrganizationIdentityContextResolver identityContexts;
    private final Clock clock;

    @Autowired
    public ProviderReplacementDryRunService(ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts, ProviderRegistry registry, WorkspaceCapabilityService capabilities,
            ProviderSelectionRepository selections, ProductProfileOverrideRepository profiles,
            AuditEventPublisher audit, MigrationRunEvidenceRepository evidence,
            OrganizationIdentityContextResolver identities) {
        this(providerCategoryCatalog, providerCapabilityContracts, registry, capabilities, selections, profiles, audit, evidence, identities, Clock.systemUTC());
    }

    public ProviderReplacementDryRunService(ProviderCategoryCatalog providerCategoryCatalog,
            ProviderCapabilityContracts providerCapabilityContracts, ProviderRegistry registry, WorkspaceCapabilityService capabilities,
            ProviderSelectionRepository selections, ProductProfileOverrideRepository profiles,
            AuditEventPublisher audit, MigrationRunEvidenceRepository evidence,
            OrganizationIdentityContextResolver identities, Clock clock) {
        this.providerCategoryCatalog = java.util.Objects.requireNonNull(providerCategoryCatalog);
        this.providerCapabilityContracts = java.util.Objects.requireNonNull(providerCapabilityContracts);
        this.providerRegistry = java.util.Objects.requireNonNull(registry);
        this.workspaceCapabilityService = java.util.Objects.requireNonNull(capabilities);
        this.providerSelectionRepository = java.util.Objects.requireNonNull(selections);
        this.productProfileOverrideRepository = java.util.Objects.requireNonNull(profiles);
        this.auditEventPublisher = java.util.Objects.requireNonNull(audit);
        this.migrationRunEvidenceRepository = java.util.Objects.requireNonNull(evidence);
        this.identityContexts = java.util.Objects.requireNonNull(identities);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public ProviderReplacementDryRunResponse dryRunProviderReplacement(ProviderReplacementDryRunRequest request, Jwt jwt) {
        workspaceCapabilityService.requireCapability(jwt, "admin.provider.configure", "admin-control-plane", "provider-replacement-dry-run");
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
        if (providerCategoryCatalog.category(category).isEmpty()) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-category-unknown",
                    "Provider category is not part of the Weave canonical control-plane contract.",
                    Map.of("category", category));
        }
        if (!providerMatchesCategory(currentAdapter, category) || !providerMatchesCategory(targetAdapter, category)) {
            throw new ApiErrorException(
                    HttpStatus.BAD_REQUEST,
                    "provider-replacement-category-mismatch",
                    "Provider replacement adapters must both be registered as support-safe candidates for the selected category.",
                    Map.of("category", category, "adapters", "unsupported-adapter-redacted"));
        }
        String choiceModel = selectionChoiceModel(request.choiceModel());
        requiredSecretRef(request.secretRef());
        String declaredSourceOfTruth = safeSourceOfTruth(request.sourceOfTruth());
        List<String> adminNotes = safeLossyMappingNotes(request.lossyMappingNotes());
        if ("files".equals(category)) {
            return blockedFilesReplacementDryRun(
                    jwt, category, currentAdapter, targetAdapter, choiceModel,
                    declaredSourceOfTruth, adminNotes);
        }
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
        ProviderReplacementDryRunResponse.ConsequencePreview consequencePreview =
                consequencePreview(category, matrixChatDryRun, adminNotes, conflicts);
        ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport noUnaccountedDataLossReport =
                noUnaccountedDataLossReport(category, matrixChatDryRun, adminNotes);
        ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof boundedProof =
                boundedProof(category, matrixChatDryRun, auditRef);
        List<ProviderReplacementDryRunResponse.CrossDomainImpactItem> crossDomainImpact =
                crossDomainImpact(category, matrixChatDryRun, auditRef);
        ProviderReplacementDryRunResponse.BaselineSnapshot baselineSnapshot =
                baselineSnapshot(category, currentAdapter, jwt, dryRunId, auditRef);
        List<String> memberImpactStates = matrixChatDryRun
                ? List.of("available", "degraded", "unavailable", "coming_later")
                : List.of("available", "disabled_by_policy", "degraded", "coming_later");
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
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
                        Map.entry("migrationEvidenceRef", "migration:" + dryRunId + ":" + category + ":dry-run-report"),
                        Map.entry("baselineComparisonRef", "migration:" + dryRunId + ":" + category + ":read-model-comparison"),
                        Map.entry("portableExportImportRequired", request.portableExportImportRequired()),
                        Map.entry("lossyMappingNoteCount", adminNotes.size()),
                        Map.entry("rawProviderError", "redacted before audit"),
                        Map.entry("token", "not-stored"))));
        persistProviderReplacementEvidence(
                dryRunId,
                category,
                auditRef,
                baselineSnapshot,
                noUnaccountedDataLossReport,
                consequencePreview,
                boundedProof,
                crossDomainImpact,
                matrixChatDryRun);
        ProviderReplacementDryRunResponse.ReadModelComparison readModelComparison =
                readModelComparison(baselineSnapshot, memberImpactStates, true);
        List<String> evidenceRefs = providerReplacementEvidenceRefs(dryRunId, category, auditRef, matrixChatDryRun);
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
                        providerCapabilityContracts.canonicalObjects(category),
                        providerCapabilityContracts.lossyMappingRisks(category),
                        adminNotes,
                        conflicts,
                        providerCapabilityContracts.replacementRequirement(category)),
                new ProviderReplacementDryRunResponse.LifecycleExpectations(
                        providerCapabilityContracts.sourceOfTruth(category),
                        providerCapabilityContracts.exportDeleteExpectation(category),
                        providerCapabilityContracts.exportDeleteExpectation(category),
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
                consequencePreview,
                noUnaccountedDataLossReport,
                boundedProof,
                crossDomainImpact,
                baselineSnapshot,
                readModelComparison,
                matrixChatDryRun
                        ? List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Backend Matrix Chat proof may only exercise bounded fixture apply/cutover/rollback evidence; production cutover remains blocked.",
                                "Resolve encrypted-room history, power-level impact, media retention, audit, rollback restore-smoke, and release-claim evidence before any future production gate.")
                        : List.of(
                                "SecretRef exists and remains backend-only; raw credentials are never returned.",
                                "Admin confirms source-of-truth, export/delete, lossy mapping, and rollback/support notes.",
                                "Readiness test and migration dry-run evidence are reviewed before activation."),
                memberImpactStates,
                evidenceRefs,
                true,
                true,
                List.of(auditRef));
    }

    private ProviderReplacementDryRunResponse blockedFilesReplacementDryRun(
            Jwt jwt,
            String category,
            String currentAdapter,
            String targetAdapter,
            String choiceModel,
            String declaredSourceOfTruth,
            List<String> adminNotes) {
        String dryRunId = "provider-replacement-dry-run-files-" + Instant.now(clock).toEpochMilli();
        String auditRef = "provider-replacement-dry-run-files-" + Instant.now(clock).toEpochMilli();
        List<String> blockers = List.of(
                "Source Files inventory and effective rights have not been measured.",
                "Target readback, permission parity, write fencing and rollback have not been verified.",
                "The legacy category-wide selection cannot activate an organization Files binding.");
        ProviderReplacementDryRunResponse.BaselineSnapshot observed =
                baselineSnapshot(category, currentAdapter, jwt, dryRunId, auditRef);
        ProviderReplacementDryRunResponse.BaselineSnapshot baseline =
                new ProviderReplacementDryRunResponse.BaselineSnapshot(
                        observed.category(), observed.persistedProviderKey(), observed.persistedChoiceModel(),
                        observed.providerSelectionPersistencePosture(), observed.persistedSelectionMatchesRequest(),
                        observed.profileOverridePersistencePosture(), observed.profileOverridePresent(),
                        observed.stableMemberImpactStates(), List.of());
        auditEventPublisher.publish(new AuditEvent(
                organizationId(jwt),
                "admin-control-plane",
                actorRef(jwt),
                "provider-replacement-dry-run",
                AuditAction.PROVIDER_REPLACEMENT_DRY_RUN,
                Instant.now(clock),
                auditRef,
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of(
                        "category", "files",
                        "currentAdapter", currentAdapter,
                        "targetAdapter", targetAdapter,
                        "sourceInventoryMeasured", false,
                        "effectivePermissionsVerified", false,
                        "targetReadbackVerified", false,
                        "rollbackVerified", false,
                        "result", "blocked-unverified")));
        return new ProviderReplacementDryRunResponse(
                dryRunId,
                "dry-run-blocked-for-apply",
                "dry-run",
                category,
                currentAdapter,
                targetAdapter,
                choiceModel,
                declaredSourceOfTruth,
                true,
                "blocked-unverified",
                true,
                new ProviderReplacementDryRunResponse.LossyMappingReport(
                        providerCapabilityContracts.canonicalObjects(category),
                        providerCapabilityContracts.lossyMappingRisks(category),
                        adminNotes,
                        blockers,
                        providerCapabilityContracts.replacementRequirement(category)),
                new ProviderReplacementDryRunResponse.LifecycleExpectations(
                        providerCapabilityContracts.sourceOfTruth(category),
                        providerCapabilityContracts.exportDeleteExpectation(category),
                        providerCapabilityContracts.exportDeleteExpectation(category),
                        "Source data and rights remain authoritative until verified cutover.",
                        "Rollback requires retained source and independently verified parity."),
                new ProviderReplacementDryRunResponse.PortableExportImportContract(
                        null, null,
                        "No source inventory or target import has been measured.",
                        List.of("Files activation remains blocked"),
                        List.of()),
                new ProviderReplacementDryRunResponse.SwitchPlan(
                        null, true, true, true, "available",
                        List.of("Keep the current Files binding active; no cutover has occurred.")),
                new ProviderReplacementDryRunResponse.ConsequencePreview(
                        0, 0, 0, 0, 0,
                        List.of("Impact counts are unmeasured; the active source remains unchanged."),
                        List.of("Rollback has not been exercised."),
                        blockers),
                new ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport(
                        0, 0, 0, 0, 0, 0,
                        List.of(), List.of(),
                        List.of("Rollback has not been exercised."),
                        List.of("All counts are unmeasured, including zero unsupported objects; no lossless migration claim is authorized.")),
                new ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof(
                        "files-preflight-unavailable", false, false, true, List.of(), blockers),
                List.of(),
                baseline,
                new ProviderReplacementDryRunResponse.ReadModelComparison(
                        true, false, true, false,
                        List.of("No Files migration evidence was recorded; the active binding was not changed.")),
                blockers,
                List.of("available", "degraded", "unavailable"),
                List.of(),
                true,
                true,
                List.of(auditRef));
    }

    private ProviderReplacementDryRunResponse.BaselineSnapshot baselineSnapshot(
            String category,
            String currentAdapter,
            Jwt jwt,
            String dryRunId,
            String auditRef) {
        Optional<ProviderSelection> persisted = providerSelectionRepository.findByCategory(category);
        ProductProfileOverride profileOverride = profileOverride(jwt);
        String providerKey = persisted.map(ProviderSelection::providerKey).orElse("not-configured");
        String choiceModel = persisted.map(ProviderSelection::choiceModel).orElse("not-configured");
        boolean selectionMatches = persisted
                .map(selection -> selection.providerKey().equalsIgnoreCase(currentAdapter))
                .orElse(false);
        return new ProviderReplacementDryRunResponse.BaselineSnapshot(
                category,
                providerKey,
                choiceModel,
                providerSelectionRepository.persistencePosture(),
                selectionMatches,
                productProfileOverrideRepository == null ? "not-wired" : productProfileOverrideRepository.persistencePosture(),
                profileOverride != null,
                STABLE_MEMBER_IMPACT_STATES,
                List.of(
                        "provider-selection:" + category + ":" + providerKey,
                        "profile-override:" + (profileOverride == null ? "absent" : "present"),
                        "migration:" + dryRunId + ":" + category + ":baseline-snapshot",
                        auditRef));
    }

    private ProductProfileOverride profileOverride(Jwt jwt) {
        if (productProfileOverrideRepository == null || jwt == null) {
            return null;
        }
        try {
            String primaryIdentityKey = identityContexts.resolve(jwt).primaryIdentityKey();
            return productProfileOverrideRepository.findByPrimaryIdentityKey(primaryIdentityKey);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private ProviderReplacementDryRunResponse.ReadModelComparison readModelComparison(
            ProviderReplacementDryRunResponse.BaselineSnapshot baselineSnapshot,
            List<String> memberImpactStates,
            boolean migrationEvidenceRecorded) {
        boolean providerNeutralStates = memberImpactStates.stream()
                .allMatch(STABLE_MEMBER_IMPACT_STATES::contains);
        List<String> findings = new ArrayList<>();
        findings.add(baselineSnapshot.persistedSelectionMatchesRequest()
                ? "requested current adapter matches persisted provider selection"
                : "requested current adapter differs from persisted provider selection and remains an admin-review input");
        findings.add(baselineSnapshot.profileOverridePresent()
                ? "product profile override was considered as provider-neutral read-model state"
                : "no product profile override exists for the actor baseline");
        findings.add(migrationEvidenceRecorded
                ? "support-safe migration run evidence was recorded for dry-run comparison"
                : "migration run evidence was not recorded");
        findings.add("northbound member states remain provider-neutral and no production provider default is flipped");
        return new ProviderReplacementDryRunResponse.ReadModelComparison(
                true,
                false,
                providerNeutralStates,
                migrationEvidenceRecorded,
                findings);
    }

    private void persistProviderReplacementEvidence(
            String dryRunId,
            String category,
            String auditRef,
            ProviderReplacementDryRunResponse.BaselineSnapshot baselineSnapshot,
            ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport noUnaccountedDataLossReport,
            ProviderReplacementDryRunResponse.ConsequencePreview consequencePreview,
            ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof boundedProof,
            List<ProviderReplacementDryRunResponse.CrossDomainImpactItem> crossDomainImpact,
            boolean matrixChatDryRun) {
        Instant now = Instant.now(clock);
        migrationRunEvidenceRepository.save(new MigrationRunEvidence(
                dryRunId,
                category,
                "dry_run_completed",
                objectCounts(noUnaccountedDataLossReport, consequencePreview),
                List.of(sha256Ref(dryRunId + ":" + category + ":" + baselineSnapshot.persistedProviderKey())),
                List.of(auditRef),
                providerReplacementArtifactRefs(dryRunId, category, matrixChatDryRun),
                providerReplacementDiagnostics(baselineSnapshot, boundedProof, crossDomainImpact),
                baselineSnapshot.persistedSelectionMatchesRequest(),
                true,
                false,
                now,
                now.plus(PROVIDER_REPLACEMENT_EVIDENCE_TTL)));
    }

    private Map<String, Integer> objectCounts(
            ProviderReplacementDryRunResponse.NoUnaccountedDataLossReport noUnaccountedDataLossReport,
            ProviderReplacementDryRunResponse.ConsequencePreview consequencePreview) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("PortableObject", noUnaccountedDataLossReport.supportedCount());
        counts.put("LossyObject", noUnaccountedDataLossReport.lossyCount());
        counts.put("UnsupportedObject", noUnaccountedDataLossReport.unsupportedCount());
        counts.put("ManualReviewObject", noUnaccountedDataLossReport.manualReviewCount());
        counts.put("ArchiveOnlyObject", noUnaccountedDataLossReport.archiveOnlyCount());
        counts.put("VendorLockedObject", noUnaccountedDataLossReport.vendorLockedCount());
        counts.put("PreservedObject", consequencePreview.preservedCount());
        return counts;
    }

    private Map<String, String> providerReplacementArtifactRefs(
            String dryRunId,
            String category,
            boolean matrixChatDryRun) {
        String prefix = "migration:" + dryRunId + ":" + category + ":";
        Map<String, String> refs = new LinkedHashMap<>();
        refs.put("baselineSnapshotRef", prefix + "baseline-snapshot");
        refs.put("readModelComparisonRef", prefix + "read-model-comparison");
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
        if (matrixChatDryRun) {
            refs.put("crossDomainImpactReportRef", prefix + "cross-domain-impact-report");
            refs.put("crossDomainRollbackRetentionRef", prefix + "cross-domain-rollback-retention");
        }
        return refs;
    }

    private List<String> providerReplacementDiagnostics(
            ProviderReplacementDryRunResponse.BaselineSnapshot baselineSnapshot,
            ProviderReplacementDryRunResponse.BoundedApplyCutoverRollbackProof boundedProof,
            List<ProviderReplacementDryRunResponse.CrossDomainImpactItem> crossDomainImpact) {
        return List.of(
                "support-safe provider replacement dry-run evidence",
                "provider-selection-posture:" + baselineSnapshot.providerSelectionPersistencePosture(),
                "profile-override-posture:" + baselineSnapshot.profileOverridePersistencePosture(),
                "bounded-proof:" + boundedProof.proofBoundary(),
                "cross-domain-impact-items:" + crossDomainImpact.size());
    }

    private List<String> providerReplacementEvidenceRefs(
            String dryRunId,
            String category,
            String auditRef,
            boolean matrixChatDryRun) {
        Map<String, String> refs = providerReplacementArtifactRefs(dryRunId, category, matrixChatDryRun);
        List<String> evidenceRefs = new ArrayList<>(refs.values());
        evidenceRefs.add(auditRef);
        return List.copyOf(evidenceRefs);
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
                Math.max(1, providerCapabilityContracts.canonicalObjects(category).size()),
                providerCapabilityContracts.lossyMappingRisks(category).size(),
                0,
                adminNotes.size(),
                0,
                0,
                providerCapabilityContracts.lossyMappingRisks(category),
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
                    false,
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
                Math.max(1, providerCapabilityContracts.canonicalObjects(category).size()),
                providerCapabilityContracts.lossyMappingRisks(category).size(),
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

    private boolean providerMatchesCategory(String providerKey, String category) {
        boolean registeredCandidate = providerRegistry.status().providers().stream()
                .filter(provider -> providerCategoryCatalog.providerMatchesCategory(provider, category))
                .anyMatch(provider -> providerKeyMatches(provider, providerKey));
        if (registeredCandidate) {
            return true;
        }
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return providerCapabilityContracts.providerCandidates(category).stream()
                .map(value -> value.toLowerCase(Locale.ROOT))
                .anyMatch(normalized::equals);
    }

    private boolean providerKeyMatches(ProviderStatusResponse provider, String providerKey) {
        String normalized = providerKey.toLowerCase(Locale.ROOT);
        return provider.providerKey().equals(providerKey)
                || provider.candidates().stream().map(value -> value.toLowerCase(Locale.ROOT)).anyMatch(normalized::equals);
    }

    private String selectionChoiceModel(String value) {
        if (value == null || value.isBlank()) {
            return "recommended_self_hosted_default";
        }
        String trimmed = value.trim();
        if (trimmed.equals("recommended_self_hosted_default")
                || trimmed.equals("external_existing_provider")
                || trimmed.equals("managed_cloud_provider")
                || trimmed.equals("hybrid_composite")) {
            return trimmed;
        }
        throw new ApiErrorException(
                HttpStatus.BAD_REQUEST,
                "provider-selection-choice-model-invalid",
                "Provider selection choice model is not part of the Weave provider choice contract.",
                Map.of("choiceModel", "invalid-choice-model-redacted"));
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

    private String sha256Ref(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder("sha256:");
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is required for provider replacement evidence", exception);
        }
    }

    private String organizationId(Jwt jwt) {
        return identityContexts.resolve(jwt).organizationId();
    }

    private String actorRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            return "actor:system";
        }
        return "user:" + jwt.getSubject();
    }
}
