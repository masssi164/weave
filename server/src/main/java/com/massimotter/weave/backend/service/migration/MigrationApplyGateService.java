package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class MigrationApplyGateService {

    private static final Set<String> LIFECYCLE = Set.of(
            "discovered",
            "preflight_failed",
            "preflight_passed",
            "exported",
            "dry_run_completed",
            "blocked",
            "approved",
            "applying",
            "applied",
            "verified",
            "rolled_back",
            "archived");
    private static final List<String> REQUIRED_ARTIFACTS = List.of(
            "dryRunReportRef",
            "exportSnapshotRef",
            "importPlanRef",
            "providerMappingRef",
            "lossyMappingReportRef",
            "conflictReportRef",
            "memberImpactPreviewRef",
            "adminApprovalRef",
            "cutoverPlanRef",
            "rollbackArchiveRef",
            "rollbackRestoreSmokeRef",
            "noUnaccountedDataLossReportRef",
            "releaseClaimBoundaryRef",
            "postApplyVerificationRef");
    private static final List<String> CHAT_CROSS_DOMAIN_REQUIRED_ARTIFACTS = List.of(
            "crossDomainImpactReportRef",
            "crossDomainManualReviewDecisionRef",
            "crossDomainRollbackRetentionRef");
    private static final Pattern UNSAFE = Pattern.compile(
            "(?i)(https?://|token|password|passwd|client[_-]?secret|authorization|bearer|cookie|private[_-]?key|secretref://|credential)");
    private static final Pattern SHA_256 = Pattern.compile("^sha256:[a-f0-9]{64}$");

    private final MigrationRunEvidenceRepository evidenceRepository;

    public MigrationApplyGateService(MigrationRunEvidenceRepository evidenceRepository) {
        this.evidenceRepository = evidenceRepository;
    }

    public MigrationApplyGateResponse evaluate(MigrationApplyGateRequest request) {
        return evidenceRepository.findCurrent(request.runId(), request.domainKey(), Instant.now())
                .map(evidence -> evaluatePersistedEvidence(request, evidence))
                .orElseGet(() -> blockedForMissingEvidence(request));
    }

    private MigrationApplyGateResponse evaluatePersistedEvidence(
            MigrationApplyGateRequest request,
            MigrationRunEvidence evidence) {
        String lifecycle = normalizeLifecycle(evidence.lifecycle());
        List<String> blockers = new ArrayList<>();
        List<String> missing = missingArtifacts(evidence);
        if (!LIFECYCLE.contains(lifecycle)) {
            blockers.add("unknown migration lifecycle state: " + safeLifecycle(evidence.lifecycle()));
        }
        if (!missing.isEmpty()) {
            blockers.add("apply blocked until required portability artifacts exist in server-side evidence: " + String.join(", ", missing));
        }
        if (evidence.objectCounts().isEmpty()) {
            blockers.add("apply blocked until object counts are recorded in server-side evidence");
        }
        if (evidence.contentHashes().isEmpty() || evidence.contentHashes().stream().anyMatch(hash -> !SHA_256.matcher(hash).matches())) {
            blockers.add("apply blocked until server-side content hashes use sha256 evidence references");
        }
        if (evidence.auditRefs().isEmpty()) {
            blockers.add("apply blocked until server-side migration audit references exist");
        }
        if (!evidence.identityMappingComplete()) {
            blockers.add("apply blocked until server-side identity mapping is complete");
        }
        if (!evidence.auditSinkAvailable()) {
            blockers.add("apply blocked until the server-side audit sink is available");
        }
        if (!evidence.adminApproved()) {
            blockers.add("apply blocked until a server-side admin approval record is present");
        }
        boolean applyAllowed = blockers.isEmpty() && Set.of("approved", "applying", "applied", "verified").contains(lifecycle);
        if (blockers.isEmpty() && !applyAllowed) {
            blockers.add("apply blocked until server-side lifecycle reaches approved after dry-run and preflight evidence");
        }
        boolean finalApplyAllowed = blockers.isEmpty() && applyAllowed;
        String effectiveLifecycle = finalApplyAllowed ? lifecycle : "blocked";
        return new MigrationApplyGateResponse(
                request.runId(),
                request.domainKey(),
                effectiveLifecycle,
                finalApplyAllowed,
                true,
                true,
                requiredArtifacts(request.domainKey()),
                missing,
                List.copyOf(blockers),
                nextActions(finalApplyAllowed, missing),
                evidenceBundle(evidence, effectiveLifecycle));
    }

    private MigrationApplyGateResponse blockedForMissingEvidence(MigrationApplyGateRequest request) {
        var blockers = List.of("apply blocked until current server-side dry-run evidence exists for this run and domain");
        return new MigrationApplyGateResponse(
                request.runId(),
                request.domainKey(),
                "blocked",
                false,
                true,
                true,
                requiredArtifacts(request.domainKey()),
                requiredArtifacts(request.domainKey()),
                blockers,
                nextActions(false, requiredArtifacts(request.domainKey())),
                new MigrationApplyGateResponse.SupportSafeEvidenceBundle(
                        request.runId(),
                        request.domainKey(),
                        "blocked",
                        Map.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        "support_safe"));
    }

    private List<String> missingArtifacts(MigrationRunEvidence evidence) {
        Map<String, String> refs = evidence.artifactRefs();
        return requiredArtifacts(evidence.domainKey()).stream()
                .filter(name -> blank(refs.get(name)))
                .toList();
    }

    private List<String> requiredArtifacts(String domainKey) {
        if ("chat".equals(domainKey)) {
            List<String> required = new ArrayList<>(REQUIRED_ARTIFACTS);
            required.addAll(CHAT_CROSS_DOMAIN_REQUIRED_ARTIFACTS);
            return List.copyOf(required);
        }
        return REQUIRED_ARTIFACTS;
    }

    private MigrationApplyGateResponse.SupportSafeEvidenceBundle evidenceBundle(
            MigrationRunEvidence evidence,
            String lifecycle) {
        return new MigrationApplyGateResponse.SupportSafeEvidenceBundle(
                evidence.runId(),
                evidence.domainKey(),
                lifecycle,
                evidence.objectCounts(),
                evidence.contentHashes(),
                evidence.auditRefs().stream().map(this::redact).toList(),
                orderedArtifactRefs(evidence).stream().filter(value -> !blank(value)).map(this::redact).toList(),
                evidence.providerDiagnostics().stream().map(this::redact).toList(),
                "support_safe");
    }

    private List<String> orderedArtifactRefs(MigrationRunEvidence evidence) {
        Map<String, String> refs = new LinkedHashMap<>();
        for (String requiredArtifact : requiredArtifacts(evidence.domainKey())) {
            refs.put(requiredArtifact, evidence.artifactRefs().get(requiredArtifact));
        }
        return refs.values().stream().filter(value -> value != null && !value.isBlank()).toList();
    }

    private List<String> nextActions(boolean applyAllowed, List<String> missing) {
        if (applyAllowed) {
            return List.of(
                    "Proceed only through the feature-gated bounded apply path; this is not production cutover authorization.",
                    "Keep audit publication, no-unaccounted-data-loss, cutover, rollback/archive, and restore-smoke evidence attached to the run.");
        }
        List<String> actions = new ArrayList<>();
        if (!missing.isEmpty()) {
            actions.add("Attach missing export/import, dry-run, lossy/conflict, impact, approval, cutover, no-unaccounted-data-loss, rollback, restore-smoke, release-claim-boundary, and verification artifacts.");
            if (missing.stream().anyMatch(name -> name.startsWith("crossDomain"))) {
                actions.add("Attach the Chat cross-domain impact report, manual-review decisions, and rollback-retention evidence before any apply or cutover claim.");
            }
        }
        actions.add("Resolve identity mapping and audit-sink blockers before any apply mutation.");
        actions.add("Expose only the support-safe evidence bundle to admins and reviewers.");
        return actions;
    }

    private String normalizeLifecycle(String lifecycle) {
        return lifecycle == null ? "discovered" : lifecycle.toLowerCase(Locale.ROOT).trim().replace('-', '_');
    }

    private String safeLifecycle(String lifecycle) {
        String value = normalizeLifecycle(lifecycle);
        return UNSAFE.matcher(value).replaceAll("redacted");
    }

    private String redact(String value) {
        if (blank(value)) {
            return "";
        }
        if (UNSAFE.matcher(value).find()) {
            return "redacted-provider-value";
        }
        return value;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
