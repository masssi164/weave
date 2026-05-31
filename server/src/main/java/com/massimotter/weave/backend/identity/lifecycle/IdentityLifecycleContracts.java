package com.massimotter.weave.backend.identity.lifecycle;

import java.util.List;

public final class IdentityLifecycleContracts {
    private IdentityLifecycleContracts() {
    }

    public record ReconcileSubject(String stableId, String email, List<String> groups, List<String> roles) {
        public ReconcileSubject {
            if (stableId == null || stableId.isBlank()) {
                throw new IllegalArgumentException("stableId is required; email must never be the primary key");
            }
            groups = groups == null ? List.of() : List.copyOf(groups);
            roles = roles == null ? List.of() : List.copyOf(roles);
        }
    }

    public record ReconcileReport(String reportId, List<ReconcileSubject> subjects, List<String> unknownGroups,
            List<String> unknownRoles, String outcome, String redaction) {
        public ReconcileReport {
            subjects = subjects == null ? List.of() : List.copyOf(subjects);
            unknownGroups = unknownGroups == null ? List.of() : List.copyOf(unknownGroups);
            unknownRoles = unknownRoles == null ? List.of() : List.copyOf(unknownRoles);
            boolean actionRequired = !unknownGroups.isEmpty() || !unknownRoles.isEmpty();
            outcome = actionRequired ? "admin_action_required" : (outcome == null ? "reconciled" : outcome);
            redaction = "support_safe";
        }

        public boolean failClosed() {
            return "admin_action_required".equals(outcome);
        }
    }

    public record OffboardingDryRunReport(String reportId, String stableSubjectId, List<String> sessionImpacts,
            List<String> matrixImpacts, List<String> filesOwnershipImpacts, List<String> boardsOwnershipImpacts,
            List<String> calendarOrganizerImpacts, List<String> ownershipTransfersRequired, List<String> auditRetentionRefs,
            boolean destructiveRemovalAllowed, String redaction) {
        public OffboardingDryRunReport {
            if (stableSubjectId == null || stableSubjectId.isBlank()) {
                throw new IllegalArgumentException("stableSubjectId is required");
            }
            sessionImpacts = copy(sessionImpacts);
            matrixImpacts = copy(matrixImpacts);
            filesOwnershipImpacts = copy(filesOwnershipImpacts);
            boardsOwnershipImpacts = copy(boardsOwnershipImpacts);
            calendarOrganizerImpacts = copy(calendarOrganizerImpacts);
            ownershipTransfersRequired = copy(ownershipTransfersRequired);
            auditRetentionRefs = copy(auditRetentionRefs);
            destructiveRemovalAllowed = destructiveRemovalAllowed && ownershipTransfersRequired.isEmpty();
            redaction = "support_safe";
        }
    }

    public record RecertificationEvidence(String evidenceId, List<String> privilegedRoleRefs,
            List<String> externalGuestRefs, List<String> staleMappingRefs, boolean adminSignedOff, String redaction) {
        public RecertificationEvidence {
            privilegedRoleRefs = copy(privilegedRoleRefs);
            externalGuestRefs = copy(externalGuestRefs);
            staleMappingRefs = copy(staleMappingRefs);
            redaction = "support_safe";
        }

        public boolean promotionReady() {
            return adminSignedOff && staleMappingRefs.isEmpty();
        }
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
