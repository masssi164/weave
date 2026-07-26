package com.massimotter.weave.backend.agentruntime.port;

import java.time.Instant;
import java.util.List;

/**
 * Operator-facing lifecycle for the RuntimeProfile signing trust root.
 *
 * <p>The projection deliberately contains no private-key bytes or filesystem locations.
 * Implementations must make every operation idempotent by its operation reference and must keep the
 * previous public key trusted for the complete configured overlap window.
 */
public interface RuntimeProfileSigningKeyLifecycle {

  KeyRingState initialize(String operationRef);

  KeyRingState prepareRotation(String rotationRef);

  KeyRingState activateRotation(String rotationRef);

  KeyRingState completeRetirement(String rotationRef);

  KeyRingState current();

  record KeyRingState(
      String activeKeyId,
      String pendingKeyId,
      List<PublishedKeyState> keys,
      String activeRotationRefHash,
      String lastCompletedRotationRefHash) {
    public KeyRingState {
      if (activeKeyId == null || activeKeyId.isBlank() || keys == null || keys.isEmpty()) {
        throw new IllegalArgumentException(
            "a support-safe active signing-key projection is required");
      }
      keys = List.copyOf(keys);
    }
  }

  record PublishedKeyState(
      String keyId,
      Status status,
      Instant validFrom,
      Instant validUntil,
      boolean privateMaterialPresent) {
    public PublishedKeyState {
      if (keyId == null
          || keyId.isBlank()
          || status == null
          || validFrom == null
          || validUntil == null
          || !validUntil.isAfter(validFrom)) {
        throw new IllegalArgumentException("complete signing-key state is required");
      }
    }
  }

  enum Status {
    ACTIVE,
    PENDING,
    PREVIOUS
  }
}
