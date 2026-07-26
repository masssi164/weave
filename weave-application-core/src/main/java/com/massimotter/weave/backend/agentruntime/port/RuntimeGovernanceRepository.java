package com.massimotter.weave.backend.agentruntime.port;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeAuditCorrelation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeRevocation;
import java.time.Instant;
import java.util.Optional;

public interface RuntimeGovernanceRepository {
  RuntimeEntitlementRef activate(
      RuntimeEntitlementObservation observation,
      String activationRef,
      String auditRef,
      Instant now);

  Optional<RuntimeEntitlementRef> findCurrent(String organizationRef, String personRef);

  Optional<RuntimeEntitlementRef> findRevision(
      String organizationRef, String personRef, String entitlementRevision);

  Optional<RuntimeEntitlementRef> findEffectiveRevision(
      String organizationRef, String personRef, String entitlementRevision, Instant now);

  RuntimeRevocation revoke(
      RuntimeEntitlementRef entitlement,
      String cellRef,
      String profileHash,
      String workloadRefHash,
      String reasonCode,
      String reasonRefHash,
      String actorRefHash,
      String revocationRef,
      String auditCorrelationRef,
      Instant now);

  RuntimeAuditCorrelation appendCorrelation(RuntimeAuditCorrelation correlation);
}
