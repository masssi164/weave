package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;

public interface RuntimeProfileSigner {
  SignedRuntimeProfile sign(RuntimeProfile profile);
}
