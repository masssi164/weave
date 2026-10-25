package com.massimotter.weave.backend.matrix;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.chat.persistence.MatrixIdentityProjectionJpaEntity;
import com.massimotter.weave.backend.chat.persistence.MatrixIdentityProjectionId;
import com.massimotter.weave.backend.chat.persistence.MatrixIdentityProjectionJpaRepository;
import com.massimotter.weave.backend.security.port.MemberSessionStateStore;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Repository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Chat identity persistence with delegation to shared member-session security state. */
@Repository
public class JpaMatrixFacadeClientStateStore implements MatrixFacadeClientStateStore {

    private static final int MAX_CONCURRENT_WRITE_ATTEMPTS = 3;

    private final MatrixIdentityProjectionJpaRepository identityProjections;
    private final MemberSessionStateStore memberSessions;
    private final TransactionTemplate transactions;

    public JpaMatrixFacadeClientStateStore(
            MatrixIdentityProjectionJpaRepository identityProjections,
            @Qualifier("memberSessionStateStore") MemberSessionStateStore memberSessions,
            PlatformTransactionManager transactionManager) {
        this.identityProjections = requireNonNull(identityProjections, "identityProjections");
        this.memberSessions = requireNonNull(memberSessions, "memberSessions");
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
    public java.util.List<IdentityProjection> identityProjections(
            String tenantId, String identityIssuer, String afterUserId, int limit) {
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("Identity page is bounded to 100");
        return identityProjections
                .findByTenantIdAndIdentityIssuerAndMatrixUserIdGreaterThanOrderByMatrixUserId(
                        tenantId, identityIssuer, afterUserId,
                        org.springframework.data.domain.PageRequest.of(0, limit))
                .stream().map(entity -> new IdentityProjection(entity.tenantId(), entity.identityIssuer(),
                        entity.matrixUserId(), entity.actorRef(), entity.authorizationPrincipalRef(), entity.updatedAt()))
                .toList();
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
        memberSessions.revokeSession(sessionHash, revokedAt, expiresAt);
    }

    @Override
    public void advanceMemberCutoff(String memberHash, Instant revokedBefore, Instant expiresAt) {
        memberSessions.advanceMemberCutoff(memberHash, revokedBefore, expiresAt);
    }

    @Override
    public Optional<Instant> memberCutoff(String memberHash, Instant now) {
        return memberSessions.memberCutoff(memberHash, now);
    }

    @Override
    public void deleteExpiredSessions(Instant now) { memberSessions.deleteExpiredSessions(now); }

    @Override
    public boolean isSessionRevoked(String sessionHash, Instant now) {
        return memberSessions.isSessionRevoked(sessionHash, now);
    }
}
