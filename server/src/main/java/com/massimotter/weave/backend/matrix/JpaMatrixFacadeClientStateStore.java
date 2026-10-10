package com.massimotter.weave.backend.matrix;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionId;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionJpaRepository;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Portable Spring Data JPA adapter for Matrix identity and revocation state. */
@Repository
public class JpaMatrixFacadeClientStateStore implements MatrixFacadeClientStateStore {

    private static final int MAX_CONCURRENT_WRITE_ATTEMPTS = 3;

    private final MatrixIdentityProjectionJpaRepository identityProjections;
    private final MatrixRevokedSessionJpaRepository revokedSessions;
    private final TransactionTemplate transactions;

    public JpaMatrixFacadeClientStateStore(
            MatrixIdentityProjectionJpaRepository identityProjections,
            MatrixRevokedSessionJpaRepository revokedSessions,
            PlatformTransactionManager transactionManager) {
        this.identityProjections = requireNonNull(identityProjections, "identityProjections");
        this.revokedSessions = requireNonNull(revokedSessions, "revokedSessions");
        this.transactions = new TransactionTemplate(
                requireNonNull(transactionManager, "transactionManager"));
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public Optional<IdentityProjection> identityProjection(
            String tenantId, String identityIssuer, String matrixUserId) {
        return identityProjections.findById(
                        new MatrixIdentityProjectionId(tenantId, identityIssuer, matrixUserId))
                .map(entity -> new IdentityProjection(
                        entity.tenantId(), entity.identityIssuer(), entity.matrixUserId(),
                        entity.actorRef(), entity.authorizationPrincipalRef(), entity.updatedAt()));
    }

    @Override
    public void saveIdentityProjection(IdentityProjection projection) {
        MatrixIdentityProjectionId id = new MatrixIdentityProjectionId(
                projection.tenantId(), projection.identityIssuer(), projection.matrixUserId());
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
            boolean[] attemptedCreate = {false};
            try {
                transactions.executeWithoutResult(status ->
                        saveIdentityProjectionInTransaction(id, projection, attemptedCreate));
                return;
            } catch (OptimisticLockingFailureException | OptimisticLockException conflict) {
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> identityProjections.existsById(id));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            } catch (DataIntegrityViolationException | PersistenceException conflict) {
                if (!attemptedCreate[0]) {
                    throw conflict;
                }
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> identityProjections.existsById(id));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            }
        }
        throw new ConcurrentWriteException(
                "Concurrent Matrix identity projection did not converge.", lastConflict);
    }

    private void saveIdentityProjectionInTransaction(
            MatrixIdentityProjectionId id,
            IdentityProjection projection,
            boolean[] attemptedCreate) {
        MatrixIdentityProjectionJpaEntity existing = identityProjections.findById(id).orElse(null);
        if (existing != null && !existing.actorRef().equals(projection.actorRef())) {
            throw new IllegalArgumentException("Matrix user is already bound to another canonical actor.");
        }
        if (existing != null
                && existing.authorizationPrincipalRef().equals(projection.authorizationPrincipalRef())) {
            return;
        }
        if (existing != null && projection.updatedAt().isBefore(existing.updatedAt())) {
            throw new IllegalArgumentException("Matrix identity projection update cannot move backwards.");
        }
        if (existing == null) {
            attemptedCreate[0] = true;
            identityProjections.saveAndFlush(new MatrixIdentityProjectionJpaEntity(
                    projection.tenantId(), projection.identityIssuer(), projection.matrixUserId(),
                    projection.actorRef(), projection.authorizationPrincipalRef(), projection.updatedAt()));
            return;
        }
        existing.refreshAuthorizationProjection(
                projection.authorizationPrincipalRef(), projection.updatedAt());
        identityProjections.flush();
    }

    @Override
    public void revokeSession(String sessionHash, Instant revokedAt, Instant expiresAt) {
        Instant persistedRevokedAt = databaseInstant(revokedAt);
        Instant persistedExpiresAt = databaseInstant(expiresAt);
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
            boolean[] attemptedCreate = {false};
            try {
                transactions.executeWithoutResult(
                        status -> revokeSessionInTransaction(
                                sessionHash,
                                persistedRevokedAt,
                                persistedExpiresAt,
                                attemptedCreate));
                return;
            } catch (OptimisticLockingFailureException | OptimisticLockException conflict) {
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> revokedSessions.existsById(sessionHash));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            } catch (DataIntegrityViolationException | PersistenceException conflict) {
                if (!attemptedCreate[0]) {
                    throw conflict;
                }
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> revokedSessions.existsById(sessionHash));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            }
        }
        throw new ConcurrentWriteException(
                "Concurrent Matrix session revocation did not converge.", lastConflict);
    }

    private void revokeSessionInTransaction(
            String sessionHash,
            Instant revokedAt,
            Instant expiresAt,
            boolean[] attemptedCreate) {
        if (revokedSessions.extendExpiry(sessionHash, expiresAt) != 0) {
            // Each logout may occur after a refresh. Keep the latest bounded
            // denial window rather than expiring from the first logout.
            return;
        }
        attemptedCreate[0] = true;
        revokedSessions.saveAndFlush(
                new MatrixRevokedSessionJpaEntity(sessionHash, revokedAt, expiresAt));
    }

    @Override
    public void advanceMemberCutoff(String memberHash, Instant revokedBefore, Instant expiresAt) {
        // The database stores microseconds. Rounding a cutoff down could admit a
        // fractional-second bearer issued before the actual revocation instant.
        Instant truncatedCutoff = databaseInstant(revokedBefore);
        Instant persistedCutoff = truncatedCutoff.isBefore(revokedBefore)
                ? truncatedCutoff.plusNanos(1_000) : truncatedCutoff;
        Instant persistedExpiresAt = databaseInstant(expiresAt);
        RuntimeException lastConflict = null;
        for (int attempt = 1; attempt <= MAX_CONCURRENT_WRITE_ATTEMPTS; attempt++) {
            boolean[] attemptedCreate = {false};
            try {
                transactions.executeWithoutResult(status -> {
                    if (revokedSessions.advanceMemberCutoff(
                            memberHash, persistedCutoff, persistedExpiresAt) != 0) {
                        return;
                    }
                    attemptedCreate[0] = true;
                    revokedSessions.saveAndFlush(new MatrixRevokedSessionJpaEntity(
                            memberHash, persistedCutoff, persistedExpiresAt));
                });
                return;
            } catch (OptimisticLockingFailureException | OptimisticLockException conflict) {
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> revokedSessions.existsById(memberHash));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            } catch (DataIntegrityViolationException | PersistenceException conflict) {
                if (!attemptedCreate[0]) {
                    throw conflict;
                }
                lastConflict = conflict;
                Boolean winnerExists = transactions.execute(status -> revokedSessions.existsById(memberHash));
                if (!Boolean.TRUE.equals(winnerExists)) {
                    throw conflict;
                }
            }
        }
        throw new ConcurrentWriteException("Concurrent member revocation cutoff did not converge.", lastConflict);
    }

    @Override
    public Optional<Instant> memberCutoff(String memberHash, Instant now) {
        return revokedSessions.findById(memberHash)
                .filter(value -> value.expiresAt().isAfter(now))
                .map(MatrixRevokedSessionJpaEntity::revokedAt);
    }

    private Instant databaseInstant(Instant value) {
        return requireNonNull(value, "Matrix session timestamp").truncatedTo(ChronoUnit.MICROS);
    }

    @Override
    public void deleteExpiredSessions(Instant now) {
        transactions.executeWithoutResult(status -> revokedSessions.deleteByExpiresAtLessThanEqual(now));
    }

    @Override
    public boolean isSessionRevoked(String sessionHash, Instant now) {
        return revokedSessions.existsBySessionHashAndExpiresAtAfter(sessionHash, now);
    }
}
