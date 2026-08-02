package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProfile;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeProvisioningPlan;
import java.time.Duration;
import java.time.Instant;

/** Reads server-owned runtime policy; request data never supplies these grants or references. */
public interface RuntimePolicyAuthority {
  RuntimeProvisioningPlan provisioningPlan(RuntimePersonDirectory.ResolvedRuntimePerson person);

  RuntimeProfile runtimeProfile(
      RuntimeCell cell, String profileId, Instant issuedAt, Instant expiresAt);

  Duration profileTtl();
}
