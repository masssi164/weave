package com.massimotter.weave.backend.matrix;

import java.time.Instant;
import java.util.Optional;

/** Persistence port for restart-critical Matrix facade client state. */
public interface MatrixFacadeClientStateStore {

    Optional<IdentityProjection> identityProjection(
            String tenantId,
            String identityIssuer,
            String matrixUserId);

    void saveIdentityProjection(IdentityProjection projection);

    void revokeSession(String sessionHash, Instant revokedAt, Instant expiresAt);

    /** Advance a hashed member's revoked-before cutoff without moving either bound backwards. */
    void advanceMemberCutoff(String memberHash, Instant revokedBefore, Instant expiresAt);

    Optional<Instant> memberCutoff(String memberHash, Instant now);

    void deleteExpiredSessions(Instant now);

    boolean isSessionRevoked(String sessionHash, Instant now);

    /** A bounded, support-safe signal that concurrent persistence did not converge. */
    final class ConcurrentWriteException extends RuntimeException {

        public ConcurrentWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    record IdentityProjection(
            String tenantId,
            String identityIssuer,
            String matrixUserId,
            String actorRef,
            String authorizationPrincipalRef,
            Instant updatedAt) {
    }
}
