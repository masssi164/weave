package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import com.massimotter.weave.backend.model.migration.MigrationApplyGateResponse;
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
            "rollbackArchiveRef",
            "postApplyVerificationRef");
    private static final Pattern UNSAFE = Pattern.compile(
            "(?i)(https?://|token|password|passwd|client[_-]?secret|authorization|bearer|cookie|private[_-]?key|secretref://|credential)");
    private static final Pattern SHA_256 = Pattern.compile("^sha256:[a-f0-9]{64}$");

    public MigrationApplyGateResponse evaluate(MigrationApplyGateRequest request) {
        String lifecycle = normalizeLifecycle(request.requestedLifecycle());
        List<String> blockers = new ArrayList<>();
        List<String> missing = missingArtifacts(request);
        if (!LIFECYCLE.contains(lifecycle)) {
            blockers.add("unknown migration lifecycle state: " + safeLifecycle(request.requestedLifecycle()));
        }
        if (!missing.isEmpty()) {
            blockers.add("apply blocked until required portability artifacts exist: " + String.join(", ", missing));
        }
        if (request.objectCounts().isEmpty()) {
            blockers.add("apply blocked until object counts are recorded");
        }
        if (request.contentHashes().isEmpty() || request.contentHashes().stream().anyMatch(hash -> !SHA_256.matcher(hash).matches())) {
            blockers.add("apply blocked until content hashes use sha256 evidence references");
        }
        if (request.auditRefs().isEmpty()) {
            blockers.add("apply blocked until migration audit references exist");
        }
        if (!request.identityMappingComplete()) {
            blockers.add("apply blocked until identity mapping is complete");
        }
        if (!request.auditSinkAvailable()) {
            blockers.add("apply blocked until the audit sink is available");
        }
        if (!request.adminApproved()) {
            blockers.add("apply blocked until an admin approval record is present");
        }
        boolean applyAllowed = blockers.isEmpty() && Set.of("approved", "applying", "applied", "verified").contains(lifecycle);
        if (blockers.isEmpty() && !applyAllowed) {
            blockers.add("apply blocked until lifecycle reaches approved after dry-run and preflight evidence");
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
                REQUIRED_ARTIFACTS,
                missing,
                List.copyOf(blockers),
                nextActions(finalApplyAllowed, missing),
                evidenceBundle(request, effectiveLifecycle));
    }

    private List<String> missingArtifacts(MigrationApplyGateRequest request) {
        Map<String, String> refs = artifactRefs(request);
        return REQUIRED_ARTIFACTS.stream()
                .filter(name -> blank(refs.get(name)))
                .toList();
    }

    private MigrationApplyGateResponse.SupportSafeEvidenceBundle evidenceBundle(
            MigrationApplyGateRequest request,
            String lifecycle) {
        return new MigrationApplyGateResponse.SupportSafeEvidenceBundle(
                request.runId(),
                request.domainKey(),
                lifecycle,
                request.objectCounts(),
                request.contentHashes(),
                request.auditRefs(),
                artifactRefs(request).values().stream().filter(value -> !blank(value)).map(this::redact).toList(),
                request.providerDiagnostics().stream().map(this::redact).toList(),
                "support_safe");
    }

    private Map<String, String> artifactRefs(MigrationApplyGateRequest request) {
        Map<String, String> refs = new LinkedHashMap<>();
        refs.put("dryRunReportRef", request.dryRunReportRef());
        refs.put("exportSnapshotRef", request.exportSnapshotRef());
        refs.put("importPlanRef", request.importPlanRef());
        refs.put("providerMappingRef", request.providerMappingRef());
        refs.put("lossyMappingReportRef", request.lossyMappingReportRef());
        refs.put("conflictReportRef", request.conflictReportRef());
        refs.put("memberImpactPreviewRef", request.memberImpactPreviewRef());
        refs.put("adminApprovalRef", request.adminApprovalRef());
        refs.put("rollbackArchiveRef", request.rollbackArchiveRef());
        refs.put("postApplyVerificationRef", request.postApplyVerificationRef());
        return refs;
    }

    private List<String> nextActions(boolean applyAllowed, List<String> missing) {
        if (applyAllowed) {
            return List.of(
                    "Proceed only through the feature-gated migration apply path.",
                    "Keep audit publication and rollback/archive evidence attached to the run.");
        }
        List<String> actions = new ArrayList<>();
        if (!missing.isEmpty()) {
            actions.add("Attach missing export/import, dry-run, lossy/conflict, impact, approval, rollback, and verification artifacts.");
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
