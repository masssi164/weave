package com.massimotter.weave.backend.weaver;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;

public record WeaverApprovalReceipt(
        String receiptRef,
        String actorRef,
        String action,
        List<String> scopeRefs,
        String policyVersion,
        String expiresAt,
        String auditRef) {

    public WeaverApprovalReceipt {
        receiptRef = safe(receiptRef);
        actorRef = safe(actorRef);
        action = safe(action);
        scopeRefs = List.copyOf(scopeRefs == null ? List.of() : scopeRefs);
        policyVersion = safe(policyVersion);
        expiresAt = safe(expiresAt);
        auditRef = safe(auditRef);
    }

    public boolean validFor(String actorRef, String action, List<String> requiredScopeRefs, String expectedPolicyVersion) {
        List<String> safeRequiredScopeRefs = List.copyOf(requiredScopeRefs == null ? List.of() : requiredScopeRefs);
        String safeExpectedPolicyVersion = safe(expectedPolicyVersion).equals("unspecified") ? "policy:support-safe-bridge-v1" : safe(expectedPolicyVersion);
        return !receiptRef.isBlank()
                && this.actorRef.equals(actorRef)
                && this.action.equals(action)
                && scopeRefs.containsAll(safeRequiredScopeRefs)
                && !policyVersion.isBlank()
                && policyVersion.startsWith("policy:")
                && (safeExpectedPolicyVersion.isBlank() || policyVersion.equals(safeExpectedPolicyVersion))
                && auditRef.startsWith("audit://")
                && expiresInFuture();
    }

    private boolean expiresInFuture() {
        try {
            return Instant.parse(expiresAt).isAfter(Instant.now());
        } catch (DateTimeParseException ignored) {
            return false;
        }
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
