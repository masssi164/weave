package com.massimotter.weave.backend.testing;

import com.massimotter.weave.backend.matrix.MatrixFacadeClientStateStore;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Deterministic test adapter for Matrix facade state without weakening production JPA wiring. */
public final class InMemoryMatrixFacadeClientStateStore implements MatrixFacadeClientStateStore {

  private final ConcurrentMap<IdentityKey, IdentityProjection> identities =
      new ConcurrentHashMap<>();
  private final ConcurrentMap<String, RevocationWindow> revokedSessions =
      new ConcurrentHashMap<>();

  @Override
  public Optional<IdentityProjection> identityProjection(
      String tenantId, String identityIssuer, String matrixUserId) {
    return Optional.ofNullable(
        identities.get(new IdentityKey(tenantId, identityIssuer, matrixUserId)));
  }

  @Override
  public void saveIdentityProjection(IdentityProjection projection) {
    IdentityKey key =
        new IdentityKey(
            projection.tenantId(), projection.identityIssuer(), projection.matrixUserId());
    identities.compute(
        key,
        (ignored, existing) -> {
          if (existing != null && !existing.actorRef().equals(projection.actorRef())) {
            throw new IllegalArgumentException(
                "Matrix user is already bound to another canonical actor.");
          }
          if (existing != null && projection.updatedAt().isBefore(existing.updatedAt())) {
            throw new IllegalArgumentException(
                "Matrix identity projection update cannot move backwards.");
          }
          return projection;
        });
  }

  @Override
  public void revokeSession(String sessionHash, Instant revokedAt, Instant expiresAt) {
    RevocationWindow requested = new RevocationWindow(revokedAt, expiresAt);
    revokedSessions.merge(
        sessionHash,
        requested,
        (existing, replacement) -> {
          if (!existing.equals(replacement)) {
            throw new IllegalArgumentException(
                "Matrix revocation digest is already bound to another session window.");
          }
          return existing;
        });
  }

  @Override
  public void deleteExpiredSessions(Instant now) {
    revokedSessions.entrySet().removeIf(entry -> !entry.getValue().expiresAt().isAfter(now));
  }

  @Override
  public boolean isSessionRevoked(String sessionHash, Instant now) {
    RevocationWindow window = revokedSessions.get(sessionHash);
    return window != null && window.expiresAt().isAfter(now);
  }

  private record IdentityKey(String tenantId, String identityIssuer, String matrixUserId) {}

  private record RevocationWindow(Instant revokedAt, Instant expiresAt) {}
}
