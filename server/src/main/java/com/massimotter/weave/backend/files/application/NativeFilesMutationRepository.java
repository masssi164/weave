package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesBlobProtectionPort;
import com.massimotter.weave.backend.files.port.FilesMutationPlan.Sealed;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
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

    record CommitProbe(
            CommitOutcome outcome,
            OperationIntent intent,
            Long rangeStart,
            Long rangeEnd) {
    }

    class CorruptMutationStateException extends RuntimeException {
        public CorruptMutationStateException(String message) {
            super(message);
        }
    }
}
