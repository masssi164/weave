package com.massimotter.weave.backend.files.adapter;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.files.application.FilesMutationIntentService;
import com.massimotter.weave.backend.files.application.NativeFilesFinalizationAuthorization;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository;
import com.massimotter.weave.backend.files.application.NativeFilesLockRepository.LockAuthorizationDeniedException;
import com.massimotter.weave.backend.files.application.NativeFilesLockTokenCodec;
import com.massimotter.weave.backend.files.domain.FilesAuthority.FileLockRecord;
import com.massimotter.weave.backend.files.domain.FilesDomain.FilePath;
import com.massimotter.weave.backend.files.port.FilesAuthorityRepository.LockConflictException;
import com.massimotter.weave.backend.operation.adapter.JpaOperationIntentRepository;
import com.massimotter.weave.backend.operation.application.OperationIntentService;
import com.massimotter.weave.backend.operation.application.OperationIntentService.BeginCommand;
import com.massimotter.weave.backend.operation.domain.OperationIntent;
import com.massimotter.weave.backend.operation.domain.OperationIntent.ProtocolProjection;
import jakarta.persistence.PersistenceException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL composite transaction for native LOCK, refresh, and UNLOCK. */
@Repository
public class JpaNativeFilesLockRepository implements NativeFilesLockRepository {

    private static final int ATTEMPTS = 3;
    private static final Duration MAXIMUM_TIMEOUT = Duration.ofHours(1);

    private final FilesStreamHeadJpaRepository heads;
    private final FileLockJpaRepository locks;
    private final JpaOperationIntentRepository operations;
    private final OperationIntentService intentService;
    private final NativeFilesLockTokenCodec tokens;
    private final NativeFilesFinalizationAuthorization authorization;
    private final TransactionTemplate transactions;
    private final Clock clock;

    @Autowired
    public JpaNativeFilesLockRepository(
            FilesStreamHeadJpaRepository heads,
            FileLockJpaRepository locks,
            JpaOperationIntentRepository operations,
            OperationIntentService intentService,
            NativeFilesLockTokenCodec tokens,
            NativeFilesFinalizationAuthorization authorization,
            PlatformTransactionManager transactionManager) {
        this(
                heads,
                locks,
                operations,
                intentService,
                tokens,
                authorization,
                transactionManager,
                Clock.systemUTC());
    }

    JpaNativeFilesLockRepository(
            FilesStreamHeadJpaRepository heads,
            FileLockJpaRepository locks,
            JpaOperationIntentRepository operations,
            OperationIntentService intentService,
            NativeFilesLockTokenCodec tokens,
            NativeFilesFinalizationAuthorization authorization,
            PlatformTransactionManager transactionManager,
            Clock clock) {
        this.heads = requireNonNull(heads, "heads");
        this.locks = requireNonNull(locks, "locks");
        this.operations = requireNonNull(operations, "operations");
        this.intentService = requireNonNull(intentService, "intentService");
        this.tokens = requireNonNull(tokens, "tokens");
        this.authorization = requireNonNull(authorization, "authorization");
        this.transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    @Override
    public LockResult acquire(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            Duration timeout,
            String auditRef) {
        OperationIntent requested = requireLockIntent(candidate, equivalentCommand, "webdav-lock");
        String requiredSpace = requireText(spaceRef, "spaceRef");
        FilePath requiredPath = requireNonNull(path, "path");
        Duration boundedTimeout = bounded(timeout);
        String token = tokens.acquireToken(requested.organizationRef(), requested.operationRef());
        String resultDigest = lockResultDigest(requiredPath, token);
        return executeLockFinalization(
                requested,
                equivalentCommand,
                requiredSpace,
                requiredPath,
                boundedTimeout,
                token,
                resultDigest,
                auditRef,
                () -> {
            Instant now = now();
            lockScope(requested.organizationRef(), requiredSpace);
            OperationIntent current = resolve(requested, equivalentCommand);
            if (current.state() == OperationIntent.State.SUCCEEDED) {
                requireSuccessfulReplay(current, resultDigest);
                return new LockResult(current, token, current.updatedAt().plus(boundedTimeout), true);
            }
            if (current.state().terminal()) {
                requireTerminalFailureEvidence(current, requiredPath, auditRef);
                throw recordedFailure(current, requiredPath);
            }
            requireCreated(current);
            requireAuthorized(current, requiredSpace);
            boolean overlappingActiveLock = locks
                    .findByIdOrganizationRefAndIdSpaceRefAndReleasedAtIsNullAndExpiresAtAfterOrderByIdCanonicalPath(
                            current.organizationRef(),
                            requiredSpace,
                            FilesPersistenceTime.utc(now))
                    .stream()
                    .map(FileLockJpaEntity::toDomain)
                    .anyMatch(lock -> applies(lock.path(), requiredPath));
            if (overlappingActiveLock) {
                throw new LockConflictException(requiredPath);
            }
            FileLockId id = new FileLockId(
                    current.organizationRef(),
                    requiredSpace,
                    requiredPath.value());
            FileLockJpaEntity existing = locks.lockById(id).orElse(null);
            FileLockRecord requestedLock = new FileLockRecord(
                    current.organizationRef(),
                    requiredSpace,
                    requiredPath,
                    tokens.digest(token),
                    current.actor().personRef(),
                    1,
                    now.plus(boundedTimeout),
                    now);
            FileLockJpaEntity stored = existing == null
                    ? FileLockJpaEntity.create(id, requestedLock, 1)
                    : existing.reacquire(requestedLock);
            locks.saveAndFlush(stored);
            OperationIntent succeeded = succeed(current, resultDigest, auditRef);
            return new LockResult(succeeded, token, stored.expiresAt(), false);
        });
    }

    @Override
    public LockResult refresh(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String presentedToken,
            Duration timeout,
            String auditRef) {
        OperationIntent requested = requireLockIntent(candidate, equivalentCommand, "webdav-lock");
        String requiredSpace = requireText(spaceRef, "spaceRef");
        FilePath requiredPath = requireNonNull(path, "path");
        String token = requireText(presentedToken, "presentedToken");
        Duration boundedTimeout = bounded(timeout);
        String resultDigest = lockResultDigest(requiredPath, token);
        return executeLockFinalization(
                requested,
                equivalentCommand,
                requiredSpace,
                requiredPath,
                boundedTimeout,
                token,
                resultDigest,
                auditRef,
                () -> {
            Instant now = now();
            lockScope(requested.organizationRef(), requiredSpace);
            OperationIntent current = resolve(requested, equivalentCommand);
            if (current.state() == OperationIntent.State.SUCCEEDED) {
                requireSuccessfulReplay(current, resultDigest);
                return new LockResult(current, token, current.updatedAt().plus(boundedTimeout), true);
            }
            if (current.state().terminal()) {
                requireTerminalFailureEvidence(current, requiredPath, auditRef);
                throw recordedFailure(current, requiredPath);
            }
            requireCreated(current);
            requireAuthorized(current, requiredSpace);
            FileLockJpaEntity stored = locks.lockById(new FileLockId(
                            current.organizationRef(),
                            requiredSpace,
                            requiredPath.value()))
                    .orElseThrow(() -> new LockConflictException(requiredPath));
            Instant extendedExpiry = now.plus(boundedTimeout);
            if (!stored.refresh(
                    tokens.digest(token),
                    current.actor().personRef(),
                    now,
                    extendedExpiry)) {
                throw new LockConflictException(requiredPath);
            }
            locks.saveAndFlush(stored);
            OperationIntent succeeded = succeed(current, resultDigest, auditRef);
            return new LockResult(succeeded, token, stored.expiresAt(), false);
        });
    }

    @Override
    public UnlockResult unlock(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String presentedToken,
            String auditRef) {
        OperationIntent requested = requireLockIntent(candidate, equivalentCommand, "webdav-unlock");
        String requiredSpace = requireText(spaceRef, "spaceRef");
        FilePath requiredPath = requireNonNull(path, "path");
        String token = requireText(presentedToken, "presentedToken");
        String resultDigest = FilesMutationIntentService.digest("unlocked:" + requiredPath.value());
        return executeUnlockFinalization(
                requested,
                equivalentCommand,
                requiredSpace,
                requiredPath,
                resultDigest,
                auditRef,
                () -> {
            Instant now = now();
            lockScope(requested.organizationRef(), requiredSpace);
            OperationIntent current = resolve(requested, equivalentCommand);
            if (current.state() == OperationIntent.State.SUCCEEDED) {
                requireSuccessfulReplay(current, resultDigest);
                return new UnlockResult(current, true);
            }
            if (current.state().terminal()) {
                requireTerminalFailureEvidence(current, requiredPath, auditRef);
                throw recordedFailure(current, requiredPath);
            }
            requireCreated(current);
            requireAuthorized(current, requiredSpace);
            FileLockJpaEntity stored = locks.lockById(new FileLockId(
                            current.organizationRef(),
                            requiredSpace,
                            requiredPath.value()))
                    .orElseThrow(() -> new LockConflictException(requiredPath));
            if (!stored.release(tokens.digest(token), current.actor().personRef(), now)) {
                throw new LockConflictException(requiredPath);
            }
            locks.saveAndFlush(stored);
            return new UnlockResult(succeed(current, resultDigest, auditRef), false);
        });
    }

    private LockResult executeLockFinalization(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            Duration timeout,
            String token,
            String successResultDigest,
            String auditRef,
            Supplier<LockResult> finalization) {
        try {
            return execute(finalization);
        } catch (LockAuthorizationDeniedException denied) {
            OperationIntent outcome = settleAfterProvenRollback(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    FailureKind.AUTHORIZATION_DENIED,
                    successResultDigest,
                    auditRef);
            return lockOutcome(outcome, path, timeout, token, successResultDigest);
        } catch (LockConflictException failed) {
            OperationIntent outcome = settleAfterProvenRollback(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    FailureKind.DETERMINISTIC_FAILURE,
                    successResultDigest,
                    auditRef);
            return lockOutcome(outcome, path, timeout, token, successResultDigest);
        } catch (DataAccessException | PersistenceException persistenceFailure) {
            OperationIntent outcome = settleAfterBoundedPersistenceFailure(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    successResultDigest,
                    auditRef,
                    persistenceFailure);
            return lockOutcome(outcome, path, timeout, token, successResultDigest);
        }
    }

    private UnlockResult executeUnlockFinalization(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String successResultDigest,
            String auditRef,
            Supplier<UnlockResult> finalization) {
        try {
            return execute(finalization);
        } catch (LockAuthorizationDeniedException denied) {
            OperationIntent outcome = settleAfterProvenRollback(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    FailureKind.AUTHORIZATION_DENIED,
                    successResultDigest,
                    auditRef);
            return unlockOutcome(outcome, path, successResultDigest);
        } catch (LockConflictException failed) {
            OperationIntent outcome = settleAfterProvenRollback(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    FailureKind.DETERMINISTIC_FAILURE,
                    successResultDigest,
                    auditRef);
            return unlockOutcome(outcome, path, successResultDigest);
        } catch (DataAccessException | PersistenceException persistenceFailure) {
            OperationIntent outcome = settleAfterBoundedPersistenceFailure(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    successResultDigest,
                    auditRef,
                    persistenceFailure);
            return unlockOutcome(outcome, path, successResultDigest);
        }
    }

    private OperationIntent settleAfterBoundedPersistenceFailure(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            String successResultDigest,
            String auditRef,
            RuntimeException persistenceFailure) {
        OperationIntent observed;
        try {
            observed = transactions.execute(status -> {
                lockScope(candidate.organizationRef(), spaceRef);
                return operations.findByIdempotencyKey(
                                candidate.organizationRef(), candidate.idempotencyKey())
                        .map(current -> requireEquivalentIdentity(
                                current, candidate, equivalentCommand))
                        .orElse(null);
            });
        } catch (RuntimeException probeFailure) {
            persistenceFailure.addSuppressed(probeFailure);
            throw persistenceFailure;
        }
        if (observed != null) {
            if (observed.state() == OperationIntent.State.SUCCEEDED) {
                requireSuccessfulReplay(observed, successResultDigest);
                return observed;
            }
            if (observed.state().terminal()) {
                requireTerminalFailureEvidence(observed, path, auditRef);
                return observed;
            }
            requireCreated(observed);
            if (!operations.outboxLinks(observed.operationRef()).isEmpty()) {
                throw new CorruptLockOperationException(
                        "native Files lock non-commit probe found unexpected outbox evidence");
            }
        }
        try {
            return settleAfterProvenRollback(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    FailureKind.PERSISTENCE_FAILURE,
                    successResultDigest,
                    auditRef);
        } catch (RuntimeException settlementFailure) {
            persistenceFailure.addSuppressed(settlementFailure);
            throw persistenceFailure;
        }
    }

    private LockResult lockOutcome(
            OperationIntent outcome,
            FilePath path,
            Duration timeout,
            String token,
            String successResultDigest) {
        if (outcome.state() == OperationIntent.State.SUCCEEDED) {
            requireSuccessfulReplay(outcome, successResultDigest);
            return new LockResult(outcome, token, outcome.updatedAt().plus(timeout), true);
        }
        throw recordedFailure(outcome, path);
    }

    private UnlockResult unlockOutcome(
            OperationIntent outcome,
            FilePath path,
            String successResultDigest) {
        if (outcome.state() == OperationIntent.State.SUCCEEDED) {
            requireSuccessfulReplay(outcome, successResultDigest);
            return new UnlockResult(outcome, true);
        }
        throw recordedFailure(outcome, path);
    }

    private OperationIntent settleAfterProvenRollback(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            FailureKind failure,
            String successResultDigest,
            String auditRef) {
        beforeDeterministicSettlement(candidate);
        try {
            return execute(() -> settleBoundary(
                    candidate,
                    equivalentCommand,
                    spaceRef,
                    path,
                    failure,
                    auditRef));
        } catch (DataAccessException | PersistenceException uncertain) {
            OperationIntent winner = probeCommittedTerminal(
                    candidate,
                    equivalentCommand,
                    path,
                    successResultDigest,
                    auditRef,
                    uncertain);
            if (winner != null) {
                return winner;
            }
            throw uncertain;
        }
    }

    void beforeDeterministicSettlement(OperationIntent candidate) {
        // Package-private test seam after the failed finalization transaction has rolled back.
    }

    private OperationIntent settleBoundary(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String spaceRef,
            FilePath path,
            FailureKind failure,
            String auditRef) {
        lockScope(candidate.organizationRef(), spaceRef);
        OperationIntent current = resolve(candidate, equivalentCommand);
        if (current.state() == OperationIntent.State.SUCCEEDED) {
            return current;
        }
        if (current.state().terminal()) {
            requireTerminalFailureEvidence(current, path, auditRef);
            return current;
        }
        requireCreated(current);
        if (!operations.outboxLinks(current.operationRef()).isEmpty()) {
            throw new CorruptLockOperationException(
                    "native Files lock non-commit probe found unexpected outbox evidence");
        }
        OperationIntentService.PreparedTransition transition = intentService.prepareNativeFailure(
                current,
                failure == FailureKind.AUTHORIZATION_DENIED,
                failureResultDigest(current, path, failure),
                requireText(auditRef, "auditRef"));
        OperationIntent settled = operations.transitionWithinTransaction(
                current,
                transition.intent(),
                transition.outboxEvent());
        requireTerminalFailureEvidence(settled, path, auditRef);
        return settled;
    }

    private OperationIntent probeCommittedTerminal(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            FilePath path,
            String successResultDigest,
            String auditRef,
            RuntimeException uncertain) {
        try {
            return transactions.execute(status -> operations.findByIdempotencyKey(
                            candidate.organizationRef(),
                            candidate.idempotencyKey())
                    .map(current -> {
                        requireEquivalentIdentity(current, candidate, equivalentCommand);
                        if (current.state() == OperationIntent.State.SUCCEEDED) {
                            requireSuccessfulReplay(current, successResultDigest);
                            return current;
                        }
                        if (current.state().terminal()) {
                            requireTerminalFailureEvidence(current, path, auditRef);
                            return current;
                        }
                        return null;
                    })
                    .orElse(null));
        } catch (RuntimeException probeFailure) {
            uncertain.addSuppressed(probeFailure);
            return null;
        }
    }

    private RuntimeException recordedFailure(OperationIntent current, FilePath path) {
        return switch (current.state()) {
            case DENIED -> new LockAuthorizationDeniedException(current.operationRef());
            case FAILED -> new LockConflictException(path);
            default -> new CorruptLockOperationException(
                    "native Files lock terminal replay has an impossible state");
        };
    }

    private OperationIntent resolve(OperationIntent candidate, BeginCommand equivalentCommand) {
        OperationIntent existing = operations.findByIdempotencyKey(
                        candidate.organizationRef(),
                        candidate.idempotencyKey())
                .orElse(null);
        if (existing != null) {
            return requireEquivalentIdentity(existing, candidate, equivalentCommand);
        }
        if (candidate.state() != OperationIntent.State.CREATED) {
            throw new CorruptLockOperationException(
                    "a new native Files lock intent is not CREATED");
        }
        return operations.insertCreatedWithoutOutbox(candidate);
    }

    private OperationIntent requireEquivalentIdentity(
            OperationIntent existing,
            OperationIntent candidate,
            BeginCommand equivalentCommand) {
        OperationIntent equivalent = intentService.requireEquivalent(existing, equivalentCommand).intent();
        if (!equivalent.idempotencyKey().equals(candidate.idempotencyKey())
                || !equivalent.operationRef().equals(candidate.operationRef())
                || !equivalent.outboxRef().equals(candidate.outboxRef())) {
            throw new CorruptLockOperationException(
                    "the stored native Files lock intent has inconsistent deterministic references");
        }
        return equivalent;
    }

    private OperationIntent succeed(
            OperationIntent current,
            String resultDigest,
            String auditRef) {
        OperationIntentService.PreparedTransition transition = intentService.prepareNativeSuccess(
                current,
                resultDigest,
                requireText(auditRef, "auditRef"));
        OperationIntent succeeded = operations.transitionWithinTransaction(
                current,
                transition.intent(),
                transition.outboxEvent());
        List<JpaOperationIntentRepository.OutboxLink> outbox = operations.outboxLinks(current.operationRef());
        if (outbox.size() != 1
                || !outbox.getFirst().outboxRef().equals(succeeded.outboxRef())
                || !"operation.succeeded".equals(outbox.getFirst().eventType())) {
            throw new CorruptLockOperationException(
                    "native Files lock finalization has inconsistent outbox evidence");
        }
        return succeeded;
    }

    private void requireSuccessfulReplay(OperationIntent current, String resultDigest) {
        List<JpaOperationIntentRepository.OutboxLink> outbox = operations.outboxLinks(current.operationRef());
        if (!resultDigest.equals(current.resultDigest())
                || outbox.size() != 1
                || !outbox.getFirst().outboxRef().equals(current.outboxRef())
                || !"operation.succeeded".equals(outbox.getFirst().eventType())) {
            throw new CorruptLockOperationException(
                    "native Files lock replay evidence is incomplete or inconsistent");
        }
    }

    private void requireTerminalFailureEvidence(
            OperationIntent current,
            FilePath path,
            String auditRef) {
        List<FailureKind> allowedFailures = switch (current.state()) {
            case DENIED -> List.of(FailureKind.AUTHORIZATION_DENIED);
            case FAILED -> List.of(
                    FailureKind.DETERMINISTIC_FAILURE,
                    FailureKind.PERSISTENCE_FAILURE);
            default -> throw new CorruptLockOperationException(
                    "native Files lock failure evidence is not terminal failure state");
        };
        List<JpaOperationIntentRepository.OutboxLink> outbox = operations.outboxLinks(
                current.operationRef());
        String eventType = current.state() == OperationIntent.State.DENIED
                ? "operation.denied"
                : "operation.failed";
        boolean expectedResult = allowedFailures.stream()
                .map(failure -> failureResultDigest(current, path, failure))
                .anyMatch(digest -> digest.equals(current.resultDigest()));
        if (!expectedResult
                || !requireText(auditRef, "auditRef").equals(current.auditRef())
                || outbox.size() != 1
                || !outbox.getFirst().outboxRef().equals(current.outboxRef())
                || !eventType.equals(outbox.getFirst().eventType())) {
            throw new CorruptLockOperationException(
                    "native Files lock failure replay evidence is incomplete or inconsistent");
        }
    }

    private void requireCreated(OperationIntent current) {
        if (current.state() == OperationIntent.State.DENIED
                || current.state() == OperationIntent.State.FAILED) {
            throw new TerminalLockOperationException(current.operationRef());
        }
        if (current.state() != OperationIntent.State.CREATED) {
            throw new CorruptLockOperationException(
                    "native Files lock intent is in an impossible partial state");
        }
    }

    private OperationIntent requireLockIntent(
            OperationIntent candidate,
            BeginCommand equivalentCommand,
            String expectedOperation) {
        OperationIntent required = requireNonNull(candidate, "candidate");
        BeginCommand command = requireNonNull(equivalentCommand, "equivalentCommand");
        OperationIntent prepared = intentService.prepare(command);
        intentService.requireEquivalent(required, command);
        if (!"files".equals(required.domain())
                || !(required.projection() instanceof ProtocolProjection projection)
                || !"webdav".equals(projection.protocol())
                || !expectedOperation.equals(projection.operation())
                || !required.organizationRef().equals(command.organizationRef())
                || !required.idempotencyKey().equals(command.idempotencyKey())
                || !required.operationRef().equals(prepared.operationRef())
                || !required.outboxRef().equals(prepared.outboxRef())) {
            throw new IllegalArgumentException("native Files lock intent does not match its operation");
        }
        return required;
    }

    private void requireAuthorized(OperationIntent intent, String spaceRef) {
        if (!authorization.allowed(intent, spaceRef)) {
            throw new LockAuthorizationDeniedException(intent.operationRef());
        }
    }

    private boolean applies(FilePath lockedPath, FilePath requestPath) {
        String locked = lockedPath.value();
        String request = requestPath.value();
        return request.equals(locked)
                || request.startsWith(locked.endsWith("/") ? locked : locked + "/")
                || locked.startsWith(request.endsWith("/") ? request : request + "/");
    }

    private FilesStreamHeadJpaEntity lockScope(
            String organizationRef,
            String spaceRef) {
        FilesScopeId id = new FilesScopeId(organizationRef, spaceRef);
        return heads.lockById(id).orElseThrow(() -> new CorruptLockOperationException(
                "native Files lock scope head is missing"));
    }

    private <T> T execute(Supplier<T> transaction) {
        RuntimeException lastFailure = null;
        for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
            try {
                return transactions.execute(status -> transaction.get());
            } catch (DataAccessException | PersistenceException failure) {
                lastFailure = failure;
            }
        }
        throw requireNonNull(lastFailure, "native Files lock transaction failure");
    }

    private Duration bounded(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return MAXIMUM_TIMEOUT;
        }
        return timeout.compareTo(MAXIMUM_TIMEOUT) > 0 ? MAXIMUM_TIMEOUT : timeout;
    }

    private String lockResultDigest(FilePath path, String token) {
        return FilesMutationIntentService.digest(
                path.value() + "\n" + FilesMutationIntentService.digest(token));
    }

    private String failureResultDigest(
            OperationIntent intent,
            FilePath path,
            FailureKind failure) {
        ProtocolProjection projection = (ProtocolProjection) intent.projection();
        return FilesMutationIntentService.digest(String.join(
                "\n",
                "weave.files-lock-settlement/v1",
                projection.operation(),
                path.value(),
                failure.resultCode));
    }

    private Instant now() {
        return Instant.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private enum FailureKind {
        AUTHORIZATION_DENIED("authorization-denied"),
        DETERMINISTIC_FAILURE("lock-precondition-failed"),
        PERSISTENCE_FAILURE("persistence-failed");

        private final String resultCode;

        FailureKind(String resultCode) {
            this.resultCode = resultCode;
        }
    }
}
