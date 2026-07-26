package com.massimotter.weave.backend.matrix;

import static java.util.Objects.requireNonNull;

import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionEntity;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionId;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixIdentityProjectionJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionEntity;
import com.massimotter.weave.backend.persistence.jpa.matrix.MatrixRevokedSessionJpaRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** Portable Spring Data JPA adapter for Matrix identity and revocation state. */
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
            String tenantId, String identityIssuer, String matrixUserId) {
        return identityProjections.findById(
                        new MatrixIdentityProjectionId(tenantId, identityIssuer, matrixUserId))
                .map(entity -> new IdentityProjection(
                        entity.tenantId(), entity.identityIssuer(), entity.matrixUserId(),
                        entity.actorRef(), entity.authorizationPrincipalRef(), entity.updatedAt()));
    }

    @Override
    @Transactional
    public void saveIdentityProjection(IdentityProjection projection) {
        MatrixIdentityProjectionId id = new MatrixIdentityProjectionId(
                projection.tenantId(), projection.identityIssuer(), projection.matrixUserId());
        MatrixIdentityProjectionEntity existing = identityProjections.findById(id).orElse(null);
        if (existing != null && !existing.actorRef().equals(projection.actorRef())) {
            throw new IllegalArgumentException("Matrix user is already bound to another canonical actor.");
        }
        if (existing != null && projection.updatedAt().isBefore(existing.updatedAt())) {
            throw new IllegalArgumentException("Matrix identity projection update cannot move backwards.");
        }
        identityProjections.saveAndFlush(new MatrixIdentityProjectionEntity(
                projection.tenantId(), projection.identityIssuer(), projection.matrixUserId(),
                projection.actorRef(), projection.authorizationPrincipalRef(), projection.updatedAt()));
    }

    @Override
    @Transactional
    public void revokeSession(String sessionHash, Instant revokedAt, Instant expiresAt) {
        MatrixRevokedSessionEntity existing = revokedSessions.findById(sessionHash).orElse(null);
        if (existing != null) {
            if (!existing.revokedAt().equals(revokedAt) || !existing.expiresAt().equals(expiresAt)) {
                throw new IllegalArgumentException(
                        "Matrix revocation digest is already bound to another session window.");
            }
            return;
        }
        revokedSessions.saveAndFlush(new MatrixRevokedSessionEntity(sessionHash, revokedAt, expiresAt));
    }

    @Override
    @Transactional
    public void deleteExpiredSessions(Instant now) {
        revokedSessions.deleteByExpiresAtLessThanEqual(now);
    }

    @Override
    public boolean isSessionRevoked(String sessionHash, Instant now) {
        return revokedSessions.existsBySessionHashAndExpiresAtAfter(sessionHash, now);
    }
}
