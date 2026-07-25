package com.massimotter.weave.backend.matrix;

import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** JPA adapter for Matrix identity projection and revocation state. */
@Repository
@Transactional(readOnly = true)
public class JpaMatrixFacadeClientStateStore implements MatrixFacadeClientStateStore {

    private final MatrixIdentityProjectionJpaRepository identityProjections;
    private final MatrixRevokedSessionJpaRepository revokedSessions;

    public JpaMatrixFacadeClientStateStore(
            MatrixIdentityProjectionJpaRepository identityProjections,
            MatrixRevokedSessionJpaRepository revokedSessions) {
        this.identityProjections = requireNonNull(identityProjections, "identityProjections");
        this.revokedSessions = requireNonNull(revokedSessions, "revokedSessions");
    }

    @Override
    public Optional<IdentityProjection> identityProjection(
            String tenantId,
            String identityIssuer,
            String matrixUserId) {
        return identityProjections.findById(new MatrixIdentityProjectionId(
                        tenantId,
                        identityIssuer,
                        matrixUserId))
                .map(MatrixIdentityProjectionJpaEntity::toProjection);
    }

    @Override
    @Transactional
    public void saveIdentityProjection(IdentityProjection projection) {
        MatrixIdentityProjectionId id = new MatrixIdentityProjectionId(
                projection.tenantId(),
                projection.identityIssuer(),
                projection.matrixUserId());
        MatrixIdentityProjectionJpaEntity entity = identityProjections.findById(id)
                .orElseGet(() -> MatrixIdentityProjectionJpaEntity.create(id, projection));
        entity.apply(projection);
        identityProjections.saveAndFlush(entity);
    }

    @Override
    @Transactional
    public void revokeSession(
            String sessionHash,
            Instant revokedAt,
            Instant expiresAt) {
        MatrixRevokedSessionJpaEntity existing = revokedSessions.findById(sessionHash)
                .orElse(null);
        if (existing != null) {
            existing.requireEquivalent(revokedAt, expiresAt);
            return;
        }
        revokedSessions.saveAndFlush(
                MatrixRevokedSessionJpaEntity.create(sessionHash, revokedAt, expiresAt));
    }

    @Override
    @Transactional
    public void deleteExpiredSessions(Instant now) {
        revokedSessions.deleteByExpiresAtLessThanEqual(MatrixPersistenceTime.utc(now));
    }

    @Override
    public boolean isSessionRevoked(String sessionHash, Instant now) {
        return revokedSessions.existsBySessionHashAndExpiresAtAfter(
                sessionHash,
                MatrixPersistenceTime.utc(now));
    }
}
