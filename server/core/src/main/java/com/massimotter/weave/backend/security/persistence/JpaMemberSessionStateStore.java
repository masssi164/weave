package com.massimotter.weave.backend.security.persistence;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
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

/** Shared durable member-session state, independent of optional Chat persistence. */
@Repository("memberSessionStateStore")
public class JpaMemberSessionStateStore implements MemberSessionStateStore {
    private static final int MAX_CONCURRENT_WRITE_ATTEMPTS = 3;
    private final MatrixRevokedSessionJpaRepository revokedSessions;
    private final TransactionTemplate transactions;

    public JpaMemberSessionStateStore(MatrixRevokedSessionJpaRepository revokedSessions,
            PlatformTransactionManager transactionManager) {
        this.revokedSessions = requireNonNull(revokedSessions, "revokedSessions");
        transactions = new TransactionTemplate(requireNonNull(transactionManager, "transactionManager"));
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
