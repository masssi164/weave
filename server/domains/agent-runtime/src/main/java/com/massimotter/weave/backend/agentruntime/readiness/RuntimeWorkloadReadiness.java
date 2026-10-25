package com.massimotter.weave.backend.agentruntime.readiness;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.readiness.domain.LocalDependencyObservation;
import com.massimotter.weave.backend.readiness.port.LocalDependencyContribution;
import java.util.Objects;

/** Projects the owning reconciliation snapshot without exposing workload or credential identifiers. */
public final class RuntimeWorkloadReadiness implements LocalDependencyContribution {
    private final AgentRuntimeWorkloadReconciliationService reconciliation;

    public RuntimeWorkloadReadiness(AgentRuntimeWorkloadReconciliationService reconciliation) {
        this.reconciliation = Objects.requireNonNull(reconciliation, "reconciliation");
    }

    @Override
    public String id() {
        return "agent-runtime-workload-identities";
    }

    @Override
    public LocalDependencyObservation check() {
        if (reconciliation.supportSafeSnapshot().state() == RuntimeWorkloadReconciliationReport.State.CONVERGED) {
            return new LocalDependencyObservation(id(), "Agent runtime workload identities", "up", "ready",
                    "Agent runtime cell bindings, workload credentials, and the reserved Keycloak client namespace are reconciled.",
                    null);
        }
        return new LocalDependencyObservation(id(), "Agent runtime workload identities", "blocked", "blocked",
                "Agent runtime workload identity reconciliation has not converged.",
                "Reconcile current cells, workload SecretRefs, and the reserved Keycloak client namespace before serving runtime traffic.");
    }
}
