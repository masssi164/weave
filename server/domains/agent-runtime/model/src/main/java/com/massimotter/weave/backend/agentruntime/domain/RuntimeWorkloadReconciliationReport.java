package com.massimotter.weave.backend.agentruntime.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Cached support-safe identity consistency evidence; it is deliberately not an overall Ready claim. */
public record RuntimeWorkloadReconciliationReport(
        String schemaVersion,
        String capabilityClaim,
        State state,
        Instant observedAt,
        Instant nextReconcileAt,
        String authoritativeRevision,
        String providerRevision,
        String correlationRef,
        Counts counts,
        Long oldestActiveCredentialAgeSeconds,
        Set<Blocker> blockers) {

    public static final String SCHEMA_VERSION = "weave.agent-runtime.workload-reconciliation/v1";
    public static final String GUARDED_CLAIM = "guarded";
    private static final Pattern FINGERPRINT = Pattern.compile("sha256:[a-f0-9]{64}");

    public RuntimeWorkloadReconciliationReport {
        if (!SCHEMA_VERSION.equals(schemaVersion)) {
            throw new IllegalArgumentException("Unsupported workload reconciliation schema");
        }
        if (!GUARDED_CLAIM.equals(capabilityClaim)) {
            throw new IllegalArgumentException("Workload identity remains guarded until full live evidence passes");
        }
        if (state == null || observedAt == null || nextReconcileAt == null || !nextReconcileAt.isAfter(observedAt)) {
            throw new IllegalArgumentException("Reconciliation state and observation window are required");
        }
        requireFingerprint(authoritativeRevision, "authoritativeRevision");
        requireFingerprint(providerRevision, "providerRevision");
        requireFingerprint(correlationRef, "correlationRef");
        if (counts == null) {
            throw new IllegalArgumentException("Reconciliation counts are required");
        }
        if (oldestActiveCredentialAgeSeconds != null && oldestActiveCredentialAgeSeconds < 0) {
            throw new IllegalArgumentException("Credential age must not be negative");
        }
        blockers = blockers == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(blockers));
        if ((state == State.CONVERGED) != blockers.isEmpty()) {
            throw new IllegalArgumentException("Only a blocker-free workload identity set is converged");
        }
    }

    private static void requireFingerprint(String value, String field) {
        if (value == null || !FINGERPRINT.matcher(value).matches()) {
            throw new IllegalArgumentException(field + " must be a support-safe sha256 reference");
        }
    }

    public enum State {
        CONVERGED,
        BLOCKED,
        UNAVAILABLE
    }

    public enum Blocker {
        AWAITING_OBSERVATION,
        PROVIDER_UNAVAILABLE,
        STALE_OBSERVATION,
        MISSING_CLIENT,
        ACTIVE_CLIENT_DISABLED,
        INACTIVE_CLIENT_ENABLED,
        ORPHANED_CLIENT,
        DUPLICATE_CLIENT,
        CROSS_BOUND_CLIENT,
        UNOWNED_CLIENT,
        MALFORMED_CLIENT,
        MISSING_CREDENTIAL,
        CREDENTIAL_STATE_INVALID,
        RECONCILE_FAILURE
    }

    public record Counts(
            int authoritativeActiveCells,
            int authoritativeInactiveCells,
            int providerReservedClients,
            int activeConverged,
            int inactiveConverged,
            int missingClients,
            int disabledActiveClients,
            int enabledInactiveClients,
            int orphanedClients,
            int duplicateClientBindings,
            int crossBoundClients,
            int unownedClients,
            int malformedClients,
            int missingCredentials,
            int invalidCredentialStates,
            int credentialRotationOverlaps,
            int reconcileFailures) {

        public Counts {
            if (java.util.stream.IntStream.of(
                            authoritativeActiveCells,
                            authoritativeInactiveCells,
                            providerReservedClients,
                            activeConverged,
                            inactiveConverged,
                            missingClients,
                            disabledActiveClients,
                            enabledInactiveClients,
                            orphanedClients,
                            duplicateClientBindings,
                            crossBoundClients,
                            unownedClients,
                            malformedClients,
                            missingCredentials,
                            invalidCredentialStates,
                            credentialRotationOverlaps,
                            reconcileFailures)
                    .anyMatch(value -> value < 0)) {
                throw new IllegalArgumentException("Reconciliation counts must not be negative");
            }
        }

        public static Counts empty() {
            return new Counts(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }
    }
}
