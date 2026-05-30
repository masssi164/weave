package com.massimotter.weave.backend.service.migration;

import com.massimotter.weave.backend.model.migration.MigrationApplyGateRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MigrationApplyGateServiceTest {

    private final MigrationApplyGateService service = new MigrationApplyGateService();

    @Test
    void blocksApplyWhenRequiredArtifactsIdentityMappingOrAuditSinkAreMissing() {
        var response = service.evaluate(new MigrationApplyGateRequest(
                "migration-chat-001",
                "chat",
                "approved",
                "dry-run:chat:001",
                "export:chat:001",
                "import:chat:001",
                "mapping:chat:001",
                null,
                null,
                "impact:chat:001",
                null,
                "rollback:chat:001",
                "verify:chat:001",
                Map.of("Conversation", 2),
                List.of("sha256:1111111111111111111111111111111111111111111111111111111111111111"),
                List.of("audit:migration.dry_run:001"),
                false,
                false,
                false,
                List.of("Authorization: Bearer raw-token", "https://provider.example.invalid/private")));

        assertThat(response.applyAllowed()).isFalse();
        assertThat(response.lifecycle()).isEqualTo("blocked");
        assertThat(response.missingArtifacts()).contains("lossyMappingReportRef", "conflictReportRef", "adminApprovalRef");
        assertThat(response.blockers()).contains(
                "apply blocked until identity mapping is complete",
                "apply blocked until the audit sink is available",
                "apply blocked until an admin approval record is present");
        assertThat(response.supportSafe()).isTrue();
        assertThat(response.providerDiagnosticsRedacted()).isTrue();
        assertThat(response.evidenceBundle().redaction()).isEqualTo("support_safe");
        assertThat(response.toString())
                .doesNotContain("raw-token")
                .doesNotContain("provider.example.invalid")
                .doesNotContain("Authorization: Bearer");
    }

    @Test
    void allowsApprovedApplyOnlyWhenLifecycleAndEvidenceAreComplete() {
        var response = service.evaluate(completeRequest("approved"));

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
    void recordsExactMigrationLifecycleStatesAndBlocksPrematureApply() {
        assertThat(List.of(
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
                "archived"))
                .allSatisfy(state -> assertThat(service.evaluate(completeRequest(state)).lifecycle())
                        .isEqualTo(SetLike.applyCapable(state) ? state : "blocked"));

        assertThat(service.evaluate(completeRequest("preflight_passed")).blockers())
                .contains("apply blocked until lifecycle reaches approved after dry-run and preflight evidence");
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
