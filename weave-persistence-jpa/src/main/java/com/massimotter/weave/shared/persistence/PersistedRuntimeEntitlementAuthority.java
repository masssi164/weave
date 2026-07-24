package com.massimotter.weave.shared.persistence;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementAuthority;
import com.massimotter.weave.backend.agentruntime.port.RuntimeEntitlementDeniedException;
import com.massimotter.weave.backend.agentruntime.port.RuntimeGovernanceRepository;
import java.time.Clock;
import java.util.Objects;

/**
 * MCP-side read-only view of the short-lived IDM observation persisted by
 * weave-server. Expired observations fail closed and require server refresh.
 */
public final class PersistedRuntimeEntitlementAuthority implements RuntimeEntitlementAuthority {
    private final RuntimeGovernanceRepository governance;
    private final Clock clock;

    public PersistedRuntimeEntitlementAuthority(RuntimeGovernanceRepository governance) {
        this(governance, Clock.systemUTC());
    }

    PersistedRuntimeEntitlementAuthority(RuntimeGovernanceRepository governance, Clock clock) {
        this.governance = Objects.requireNonNull(governance, "governance");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public RuntimeEntitlementObservation observe(ObserveEntitlementCommand command) {
        RuntimeEntitlementRef current = governance.findCurrent(
                        command.organizationRef(),
                        command.personRef())
                .filter(entitlement -> entitlement.effectiveAt(clock.instant()))
                .filter(entitlement -> entitlement.memberBinding().equals(command.memberBinding()))
                .orElseThrow(() -> new RuntimeEntitlementDeniedException(
                        "The persisted entitlement observation is absent or expired"));
        return new RuntimeEntitlementObservation(
                current.organizationRef(),
                current.personRef(),
                current.memberBinding(),
                current.sourceProvider(),
                current.sourceGroupRef(),
                current.capabilityRevision(),
                current.lastObservedAt(),
                current.expiresAt());
    }
}
