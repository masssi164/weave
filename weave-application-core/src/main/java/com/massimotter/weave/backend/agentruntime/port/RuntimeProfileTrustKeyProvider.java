package com.massimotter.weave.backend.agentruntime.port;

import java.security.PublicKey;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RuntimeProfileTrustKeyProvider {
  Optional<TrustKey> resolve(String keyId, Instant now);

  List<TrustKey> publishedKeys(Instant now);

  record TrustKey(String keyId, PublicKey publicKey, Instant validFrom, Instant validUntil) {
    public TrustKey {
      if (keyId == null
          || keyId.isBlank()
          || publicKey == null
          || validFrom == null
          || validUntil == null
          || !validUntil.isAfter(validFrom)) {
        throw new IllegalArgumentException(
            "complete RuntimeProfile trust-key metadata is required");
      }
      if (!"EdDSA".equalsIgnoreCase(publicKey.getAlgorithm())
          && !"Ed25519".equalsIgnoreCase(publicKey.getAlgorithm())) {
        throw new IllegalArgumentException("RuntimeProfile trust keys must use Ed25519");
      }
    }

    public boolean validAt(Instant now) {
      return !now.isBefore(validFrom) && now.isBefore(validUntil);
    }
  }
}
