package com.massimotter.weave.backend.files.application;

import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import java.time.Duration;
import java.time.Instant;

/** Atomic PostgreSQL boundary for native Files lock-token lifecycle operations. */
public interface NativeFilesLockRepository {

    LockResult acquire(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            Duration timeout,
            String auditRef);

    LockResult refresh(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String presentedToken,
            Duration timeout,
            String auditRef);

    UnlockResult unlock(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String presentedToken,
            String auditRef);

    record LockResult(
            OperationIntent intent,
            String token,
            Instant expiresAt,
            boolean replay) {
    }

    record UnlockResult(OperationIntent intent, boolean replay) {
    }

    final class TerminalLockOperationException extends RuntimeException {
        public TerminalLockOperationException(String operationRef) {
            super("native Files lock operation is already terminal: " + operationRef);
        }
    }

    final class LockAuthorizationDeniedException extends RuntimeException {
        public LockAuthorizationDeniedException(String operationRef) {
            super("native Files lock authorization changed before finalization: " + operationRef);
        }
    }

    final class CorruptLockOperationException extends RuntimeException {
        public CorruptLockOperationException(String message) {
            super(message);
        }
    }
}
