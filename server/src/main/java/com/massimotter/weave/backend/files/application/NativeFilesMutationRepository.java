package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesBlobProtectionPort;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import java.util.List;
import java.util.function.Supplier;

/** Composite PostgreSQL boundary for the two native Files mutation transactions. */
public interface NativeFilesMutationRepository extends FilesBlobProtectionPort {

    default BeginResult begin(OperationIntent candidate, Sealed plan) {
        FilesScope scope = new FilesScope(plan.organizationRef(), plan.spaceRef());
        return begin(candidate, scope, () -> plan);
    }

    BeginResult begin(
            OperationIntent candidate,
            FilesScope scope,
            Supplier<Sealed> planFactory);

    Sealed requireSealed(String operationRef);

    FinalizationResult finalizeSuccess(
            OperationIntent expected,
            Sealed plan,
            String resultDigest,
            String auditRef,
            LockMove lockMove);

    OperationIntent recordFailure(
            OperationIntent expected,
            boolean denied,
            String resultDigest,
            String auditRef);

    OperationIntent markAmbiguous(OperationIntent expected, String correlationDigest);

    OperationIntent beginReconciliation(OperationIntent expected);

    CommitProbe probe(String operationRef);

    /** Fail-closed protection state for same-generation retained private PUT ingress. */
    IngressProtection ingressProtection(String operationRef);

    /**
     * Bounded relational authority for same-generation PUT recovery; never spool inventory.
     *
     * <p>The raw page cursor advances independently from decoded candidates so a corrupt row cannot
     * starve later recoverable operations.</p>
     */
    RecoveryPage recoverablePutMutations(String afterOperationRef, int limit);

    default List<RecoveryCandidate> recoverablePutMutations(int limit) {
        return recoverablePutMutations(null, limit).candidates();
    }

    record BeginResult(OperationIntent intent, Sealed plan, boolean created) {
    }

    record FinalizationResult(
            OperationIntent intent,
            long rangeStart,
            long rangeEnd) {
    }

    record LockMove(
            FilePath source,
            FilePath destination,
            String tokenDigest,
            String ownerRef) {
    }

    enum CommitOutcome {
        SUCCEEDED,
        NOT_COMMITTED,
        TERMINAL_FAILURE,
        CORRUPT
    }

    enum IngressProtection {
        PROTECTED,
        UNPROTECTED,
        UNAVAILABLE
    }

    record CommitProbe(
            CommitOutcome outcome,
            OperationIntent intent,
            Long rangeStart,
            Long rangeEnd) {
    }

    record RecoveryCandidate(OperationIntent intent, Sealed plan) {
    }

    record RecoveryPage(
            List<RecoveryCandidate> candidates,
            String lastScannedOperationRef,
            int scannedCount) {

        public RecoveryPage {
            candidates = List.copyOf(candidates);
            if (scannedCount < candidates.size()) {
                throw new IllegalArgumentException("scannedCount must include every candidate");
            }
            if (scannedCount == 0) {
                if (lastScannedOperationRef != null) {
                    throw new IllegalArgumentException("an empty recovery page has no cursor");
                }
            } else if (lastScannedOperationRef == null || lastScannedOperationRef.isBlank()) {
                throw new IllegalArgumentException("a non-empty recovery page requires a cursor");
            }
        }
    }

    class CorruptMutationStateException extends RuntimeException {
        public CorruptMutationStateException(String message) {
            super(message);
        }
    }
}
