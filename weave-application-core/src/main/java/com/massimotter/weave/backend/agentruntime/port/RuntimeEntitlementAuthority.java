package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;

/**
 * Reads current entitlement from the configured IDM; it never trusts request claims as authority.
 */
public interface RuntimeEntitlementAuthority {
  RuntimeEntitlementObservation observe(ObserveEntitlementCommand command);

  record ObserveEntitlementCommand(
      String organizationRef,
      String personRef,
      RuntimeMemberBinding memberBinding,
      String auditRef) {
    public ObserveEntitlementCommand {
      if (organizationRef == null
          || organizationRef.isBlank()
          || personRef == null
          || personRef.isBlank()
          || memberBinding == null
          || auditRef == null
          || auditRef.isBlank()) {
        throw new IllegalArgumentException("complete entitlement observation context is required");
      }
    }
  }
}
