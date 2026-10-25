package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.port.RuntimeProfileTrustKeyProvider;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public final class UnavailableRuntimeProfileTrustKeyProvider
    implements RuntimeProfileTrustKeyProvider {
  @Override
  public Optional<TrustKey> resolve(String keyId, Instant now) {
    return Optional.empty();
  }

  @Override
  public List<TrustKey> publishedKeys(Instant now) {
    return List.of();
  }
}
