package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfileJwkSet;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustBundlePublisher;
import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import java.math.BigInteger;
import java.security.interfaces.EdECPublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class Ed25519RuntimeProfileTrustBundlePublisher
    implements RuntimeProfileTrustBundlePublisher {
  private final RuntimeProfileTrustKeyProvider trustKeys;

  public Ed25519RuntimeProfileTrustBundlePublisher(RuntimeProfileTrustKeyProvider trustKeys) {
    if (trustKeys == null) {
      throw new IllegalArgumentException("RuntimeProfile trust-key provider is required");
    }
    this.trustKeys = trustKeys;
  }

  @Override
  public Optional<RuntimeProfileJwkSet> publish(Instant now) {
    if (now == null) {
      throw new IllegalArgumentException("trust publication time is required");
    }
    List<RuntimeProfileTrustKeyProvider.TrustKey> current =
        trustKeys.publishedKeys(now).stream()
            .filter(key -> key.validAt(now))
            .sorted(Comparator.comparing(RuntimeProfileTrustKeyProvider.TrustKey::keyId))
            .toList();
    Set<String> keyIds = new HashSet<>();
    List<RuntimeProfileJwkSet.Jwk> projected = new ArrayList<>();
    for (RuntimeProfileTrustKeyProvider.TrustKey key : current) {
      if (!keyIds.add(key.keyId())) {
        throw new IllegalStateException(
            "RuntimeProfile trust provider returned an ambiguous key id");
      }
      if (!(key.publicKey() instanceof EdECPublicKey ed25519)) {
        throw new IllegalStateException("RuntimeProfile trust provider returned a non-Ed25519 key");
      }
      projected.add(
          new RuntimeProfileJwkSet.Jwk(
              "OKP", "Ed25519", encodePoint(ed25519), "sig", "EdDSA", key.keyId()));
    }
    return projected.isEmpty()
        ? Optional.empty()
        : Optional.of(new RuntimeProfileJwkSet(projected));
  }

  private static String encodePoint(EdECPublicKey key) {
    byte[] encoded = littleEndian(key.getPoint().getY(), 32);
    if (key.getPoint().isXOdd()) {
      encoded[31] |= (byte) 0x80;
    }
    return Base64.getUrlEncoder().withoutPadding().encodeToString(encoded);
  }

  private static byte[] littleEndian(BigInteger value, int length) {
    byte[] bigEndian = value.toByteArray();
    int first = bigEndian.length > 1 && bigEndian[0] == 0 ? 1 : 0;
    int significant = bigEndian.length - first;
    if (significant > length) {
      throw new IllegalStateException("Ed25519 public point exceeds 32 bytes");
    }
    byte[] result = new byte[length];
    for (int index = 0; index < significant; index++) {
      result[index] = bigEndian[bigEndian.length - 1 - index];
    }
    return result;
  }
}
