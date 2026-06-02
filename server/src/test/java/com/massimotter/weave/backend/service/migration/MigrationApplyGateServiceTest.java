package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationApplyGateServiceTest {

    private InMemoryMigrationRunEvidenceRepository repository;
    private MigrationApplyGateService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryMigrationRunEvidenceRepository();
        service = new MigrationApplyGateService(repository);
    }

    @Test
    void rejectsForgedCompleteLookingRequestWhenServerEvidenceIsMissing() {
        var response = service.evaluate(completeRequest("approved"));

        assertThat(response.applyAllowed()).isFalse();
        assertThat(response.lifecycle()).isEqualTo("blocked");
        assertThat(response.blockers())
                .contains("apply blocked until current server-side dry-run evidence exists for this run and domain");
        assertThat(response.missingArtifacts()).contains("dryRunReportRef", "adminApprovalRef");
        assertThat(response.evidenceBundle().artifactRefs()).isEmpty();
    }

    @Test
    void blocksApplyWhenServerEvidenceLacksArtifactsIdentityMappingAuditSinkOrApproval() {
        Map<String, String> partialRefs = new LinkedHashMap<>();
        partialRefs.put("dryRunReportRef", "dry-run:boards:001");
        partialRefs.put("exportSnapshotRef", "export:boards:001");
        partialRefs.put("importPlanRef", "import:boards:001");
        partialRefs.put("providerMappingRef", "mapping:boards:001");
        partialRefs.put("memberImpactPreviewRef", "impact:boards:001");
        partialRefs.put("cutoverPlanRef", "cutover:boards:001");
        partialRefs.put("rollbackArchiveRef", "rollback:boards:001");
        partialRefs.put("rollbackRestoreSmokeRef", "restore-smoke:boards:001");
        partialRefs.put("noUnaccountedDataLossReportRef", "no-loss:boards:001");
        partialRefs.put("releaseClaimBoundaryRef", "claim-boundary:boards:001");
        partialRefs.put("postApplyVerificationRef", "verify:boards:001");
        repository.save(evidence("approved", partialRefs, false, false, false));

        var response = service.evaluate(completeRequest("approved"));

        assertThat(response.applyAllowed()).isFalse();
        assertThat(response.lifecycle()).isEqualTo("blocked");
        assertThat(response.missingArtifacts()).contains("lossyMappingReportRef", "conflictReportRef", "adminApprovalRef");
        assertThat(response.blockers()).contains(
                "apply blocked until server-side identity mapping is complete",
                "apply blocked until the server-side audit sink is available",
                "apply blocked until a server-side admin approval record is present");
        assertThat(response.supportSafe()).isTrue();
        assertThat(response.providerDiagnosticsRedacted()).isTrue();
        assertThat(response.evidenceBundle().redaction()).isEqualTo("support_safe");
        assertThat(response.toString())
                .doesNotContain("raw-token")
                .doesNotContain("provider.example.invalid")
                .doesNotContain("Authorization: Bearer");
    }

    @Test
    void allowsApprovedApplyOnlyWhenPersistedLifecycleAndEvidenceAreComplete() {
        repository.save(evidence("approved", completeArtifactRefs(), true, true, true));

        var response = service.evaluate(completeRequest("discovered"));

        assertThat(response.applyAllowed()).isTrue();
        assertThat(response.lifecycle()).isEqualTo("approved");
        assertThat(response.blockers()).isEmpty();
        assertThat(response.evidenceBundle().artifactRefs()).contains(
                "dry-run:boards:001",
                "export:boards:001",
                "import:boards:001",
                "approval:boards:001",
                "verify:boards:001");
    }

    @Test
    void chatApplyRequiresCrossDomainImpactManualReviewAndRollbackRetentionEvidence() {
        Map<String, String> refs = completeArtifactRefs("chat");
        refs.remove("crossDomainImpactReportRef");
        refs.remove("crossDomainManualReviewDecisionRef");
        refs.remove("crossDomainRollbackRetentionRef");
        repository.save(evidence("migration-chat-001", "chat", "approved", refs, true, true, true));

        var response = service.evaluate(new MigrationApplyGateRequest(
                "migration-chat-001",
                "chat",
                "approved",
                "dry-run:chat:001",
                "export:chat:001",
                "import:chat:001",
                "mapping:chat:001",
                "lossy:chat:001",
                "conflict:chat:001",
                "impact:chat:001",
                "approval:chat:001",
                "rollback:chat:001",
                "verify:chat:001",
                Map.of("Conversation", 1),
                List.of("sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                List.of("audit:migration.dry_run:chat:001"),
                true,
                true,
                true,
                List.of("support-safe diagnostic")));

        assertThat(response.applyAllowed()).isFalse();
        assertThat(response.lifecycle()).isEqualTo("blocked");
        assertThat(response.requiredArtifacts()).contains(
                "crossDomainImpactReportRef",
                "crossDomainManualReviewDecisionRef",
                "crossDomainRollbackRetentionRef");
        assertThat(response.missingArtifacts()).contains(
                "crossDomainImpactReportRef",
                "crossDomainManualReviewDecisionRef",
                "crossDomainRollbackRetentionRef");
        assertThat(response.nextActions())
                .anySatisfy(action -> assertThat(action).contains("Chat cross-domain impact report"));
    }

    @Test
    void staleServerEvidenceFailsClosed() {
        Instant now = Instant.now();
        repository.save(new MigrationRunEvidence(
                "migration-boards-001",
                "boards",
                "approved",
                Map.of("Board", 1),
                List.of("sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                List.of("audit:migration.dry_run:001"),
                completeArtifactRefs(),
                List.of(),
                true,
                true,
                true,
                now.minusSeconds(172800),
                now.minusSeconds(86400)));

        var response = service.evaluate(completeRequest("approved"));

        assertThat(response.applyAllowed()).isFalse();
        assertThat(response.blockers())
                .contains("apply blocked until current server-side dry-run evidence exists for this run and domain");
    }

    @Test
    void recordsExactMigrationLifecycleStatesAndBlocksPrematureApply() {
        for (String state : List.of(
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
                "archived")) {
            repository.clear();
            repository.save(evidence(state, completeArtifactRefs(), true, true, true));
            assertThat(service.evaluate(completeRequest("approved")).lifecycle())
                    .isEqualTo(SetLike.applyCapable(state) ? state : "blocked");
        }

        repository.clear();
        repository.save(evidence("preflight_passed", completeArtifactRefs(), true, true, true));
        assertThat(service.evaluate(completeRequest("approved")).blockers())
                .contains("apply blocked until server-side lifecycle reaches approved after dry-run and preflight evidence");
    }

    private MigrationRunEvidence evidence(String lifecycle, Map<String, String> artifactRefs,
            boolean identityMappingComplete, boolean auditSinkAvailable, boolean adminApproved) {
        return evidence("migration-boards-001", "boards", lifecycle, artifactRefs, identityMappingComplete, auditSinkAvailable, adminApproved);
    }

    private MigrationRunEvidence evidence(String runId, String domainKey, String lifecycle, Map<String, String> artifactRefs,
            boolean identityMappingComplete, boolean auditSinkAvailable, boolean adminApproved) {
        Instant now = Instant.now();
        return new MigrationRunEvidence(
                runId,
                domainKey,
                lifecycle,
                Map.of("Board", 1, "Task", 12),
                List.of("sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                List.of("audit:migration.dry_run:001", "audit:migration.apply_requested:001"),
                artifactRefs,
                List.of("Authorization: Bearer raw-token", "https://provider.example.invalid/private"),
                identityMappingComplete,
                auditSinkAvailable,
                adminApproved,
                now,
                now.plusSeconds(3600));
    }

    private Map<String, String> completeArtifactRefs() {
        return completeArtifactRefs("boards");
    }

    private Map<String, String> completeArtifactRefs(String domain) {
        Map<String, String> refs = new LinkedHashMap<>();
        refs.put("dryRunReportRef", "dry-run:" + domain + ":001");
        refs.put("exportSnapshotRef", "export:" + domain + ":001");
        refs.put("importPlanRef", "import:" + domain + ":001");
        refs.put("providerMappingRef", "mapping:" + domain + ":001");
        refs.put("lossyMappingReportRef", "lossy:" + domain + ":001");
        refs.put("conflictReportRef", "conflict:" + domain + ":001");
        refs.put("memberImpactPreviewRef", "impact:" + domain + ":001");
        refs.put("adminApprovalRef", "approval:" + domain + ":001");
        refs.put("cutoverPlanRef", "cutover:" + domain + ":001");
        refs.put("rollbackArchiveRef", "rollback:" + domain + ":001");
        refs.put("rollbackRestoreSmokeRef", "restore-smoke:" + domain + ":001");
        refs.put("noUnaccountedDataLossReportRef", "no-loss:" + domain + ":001");
        refs.put("releaseClaimBoundaryRef", "claim-boundary:" + domain + ":001");
        refs.put("postApplyVerificationRef", "verify:" + domain + ":001");
        if ("chat".equals(domain)) {
            refs.put("crossDomainImpactReportRef", "impact:chat:cross-domain:001");
            refs.put("crossDomainManualReviewDecisionRef", "manual-review:chat:cross-domain:001");
            refs.put("crossDomainRollbackRetentionRef", "rollback-retention:chat:cross-domain:001");
        }
        return refs;
    }

    private MigrationApplyGateRequest completeRequest(String lifecycle) {
        return new MigrationApplyGateRequest(
                "migration-boards-001",
                "boards",
                lifecycle,
                "dry-run:boards:001",
                "export:boards:001",
                "import:boards:001",
                "mapping:boards:001",
                "lossy:boards:001",
                "conflict:boards:001",
                "impact:boards:001",
                "approval:boards:001",
                "rollback:boards:001",
                "verify:boards:001",
                Map.of("Board", 1, "Task", 12),
                List.of("sha256:2222222222222222222222222222222222222222222222222222222222222222"),
                List.of("audit:migration.dry_run:001", "audit:migration.apply_requested:001"),
                true,
                true,
                true,
                List.of("support-safe diagnostic"));
    }

    private static final class SetLike {
        private static boolean applyCapable(String state) {
            return List.of("approved", "applying", "applied", "verified").contains(state);
        }
    }
}
