package com.massimotter.weave.backend.identity.lifecycle;

import java.util.List;
import org.junit.jupiter.api.Test;

import static com.massimotter.weave.backend.identity.lifecycle.IdentityLifecycleContracts.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdentityLifecycleContractsTest {

    @Test
    void reconcileUsesStableIdsAndFailsClosedForUnknownGroupsOrRoles() {
        var subject = new ReconcileSubject("idp-user-123", "person@example.invalid", List.of("unknown-group"), List.of("member"));
        var report = new ReconcileReport("reconcile-1", List.of(subject), List.of("unknown-group"), List.of("unknown-role"), "reconciled", "raw");

        assertThat(report.failClosed()).isTrue();
        assertThat(report.outcome()).isEqualTo("admin_action_required");
        assertThat(report.redaction()).isEqualTo("support_safe");
        assertThat(report.toString()).doesNotContain("password").doesNotContain("Bearer");
        assertThatThrownBy(() -> new ReconcileSubject("", "email-only@example.invalid", List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stableId");
    }

    @Test
    void offboardingDryRunRequiresOwnershipTransferBeforeDestructiveRemoval() {
        var report = new OffboardingDryRunReport(
                "offboard-1",
                "idp-user-123",
                List.of("revoke active sessions"),
                List.of("remove Matrix membership and devices after export policy review"),
                List.of("transfer Files ownership and shares"),
                List.of("transfer Boards ownership and watchers"),
                List.of("transfer Calendar organizer/resource duties"),
                List.of("files-owner-transfer", "boards-owner-transfer"),
                List.of("audit-retention:subject-123"),
                true,
                "raw");

        assertThat(report.destructiveRemovalAllowed()).isFalse();
        assertThat(report.redaction()).isEqualTo("support_safe");
        assertThat(report.filesOwnershipImpacts()).contains("transfer Files ownership and shares");
    }

    @Test
    void recertificationEvidenceBlocksPromotionWithStaleMappings() {
        var evidence = new RecertificationEvidence(
                "recert-1",
                List.of("role:org-admin"),
                List.of("guest:external-1"),
                List.of("mapping:stale-1"),
                true,
                "raw");

        assertThat(evidence.promotionReady()).isFalse();
        assertThat(evidence.redaction()).isEqualTo("support_safe");
    }
}
