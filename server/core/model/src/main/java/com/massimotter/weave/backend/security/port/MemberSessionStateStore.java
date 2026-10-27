package com.massimotter.weave.backend.security.port;

import java.time.Instant;
import java.util.Optional;

/** Durable, hashed session tombstones and member revocation epochs shared by all domains. */
public interface MemberSessionStateStore {
    void revokeSession(String sessionHash, Instant revokedAt, Instant expiresAt);

    /** Advance a hashed member's revoked-before cutoff without moving either bound backwards. */
    void advanceMemberCutoff(String memberHash, Instant revokedBefore, Instant expiresAt);

    Optional<Instant> memberCutoff(String memberHash, Instant now);

    void deleteExpiredSessions(Instant now);

    boolean isSessionRevoked(String sessionHash, Instant now);

    /** A bounded, support-safe signal that concurrent persistence did not converge. */
    final class ConcurrentWriteException extends RuntimeException {
        public ConcurrentWriteException(String message, Throwable cause) { super(message, cause); }
    }
}
