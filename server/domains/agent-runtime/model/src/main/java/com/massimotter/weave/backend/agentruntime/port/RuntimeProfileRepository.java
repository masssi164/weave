package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import java.time.Instant;
import java.util.Optional;

public interface RuntimeProfileRepository {
    SignedRuntimeProfile activate(RuntimeCell expectedCell, SignedRuntimeProfile profile, Instant now);

    Optional<SignedRuntimeProfile> findCurrentForWorkload(
            String profileHash,
            String workloadIssuer,
            String workloadSubject,
            String workloadClientId,
            Instant now);

    void revokeCurrent(String cellRef, String revocationCode, Instant now);
}
