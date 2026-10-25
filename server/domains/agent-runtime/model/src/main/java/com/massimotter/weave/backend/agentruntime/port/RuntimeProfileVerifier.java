package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import java.time.Instant;

public interface RuntimeProfileVerifier {
  RuntimeProfile verify(SignedRuntimeProfile envelope, Instant now);
}
