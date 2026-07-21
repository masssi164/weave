package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfileJwkSet;
import java.time.Instant;
import java.util.Optional;

public interface RuntimeProfileTrustBundlePublisher {
    Optional<RuntimeProfileJwkSet> publish(Instant now);
}
