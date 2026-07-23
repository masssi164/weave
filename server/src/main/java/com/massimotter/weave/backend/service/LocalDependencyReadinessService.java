package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LocalDependencyReadinessService {

    private final PersistenceHealthProbe persistenceHealth;
    private final RuntimeStateStore runtimeStateStore;
    private final AgentRuntimeWorkloadReconciliationService workloadReconciliation;

    public LocalDependencyReadinessService(
            PersistenceHealthProbe persistenceHealth,
            Optional<RuntimeStateStore> runtimeStateStore,
            Optional<AgentRuntimeWorkloadReconciliationService> workloadReconciliation) {
        this.persistenceHealth = persistenceHealth;
        this.runtimeStateStore = runtimeStateStore.orElse(null);
        this.workloadReconciliation = workloadReconciliation.orElse(null);
    }

    public List<PlatformStatusResponse.DiagnosticCheck> checks() {
        List<PlatformStatusResponse.DiagnosticCheck> checks = new ArrayList<>();
        checks.add(persistenceCheck());
        if (runtimeStateStore != null) {
            checks.add(runtimeStateCheck());
        }
        if (workloadReconciliation != null) {
            checks.add(workloadIdentityCheck());
        }
        return List.copyOf(checks);
    }

    private PlatformStatusResponse.DiagnosticCheck workloadIdentityCheck() {
        RuntimeWorkloadReconciliationReport report = workloadReconciliation.supportSafeSnapshot();
        if (report.state() == RuntimeWorkloadReconciliationReport.State.CONVERGED) {
            return new PlatformStatusResponse.DiagnosticCheck(
                    "agent-runtime-workload-identities",
                    "Agent runtime workload identities",
                    "up",
                    "ready",
                    "Agent runtime cell bindings, workload credentials, and the reserved Keycloak client namespace are reconciled.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticCheck(
                "agent-runtime-workload-identities",
                "Agent runtime workload identities",
                "blocked",
                "blocked",
                "Agent runtime workload identity reconciliation has not converged.",
                "Reconcile current cells, workload SecretRefs, and the reserved Keycloak client namespace before serving runtime traffic.");
    }

    private PlatformStatusResponse.DiagnosticCheck runtimeStateCheck() {
        RuntimeStateStore.StoreReadiness readiness = runtimeStateStore.readiness();
        if (readiness.ready()) {
            return new PlatformStatusResponse.DiagnosticCheck(
                    "agent-runtime-state",
                    "Agent runtime state",
                    "up",
                    "ready",
                    "Encrypted external runtime state is available in the guarded self-hosted profile.",
                    null);
        }
        return new PlatformStatusResponse.DiagnosticCheck(
                "agent-runtime-state",
                "Agent runtime state",
                "blocked",
                "blocked",
                "Encrypted external runtime state is unavailable.",
                "Restore and reconcile the runtime-state database and wrapping-key SecretRef set.");
    }

    private PlatformStatusResponse.DiagnosticCheck persistenceCheck() {
        try {
            if (persistenceHealth.ready()) {
                return new PlatformStatusResponse.DiagnosticCheck(
                        "persistence",
                        "Persistence",
                        "up",
                        "ready",
                        "Configured persistence is reachable.",
                        null);
            }
        } catch (RuntimeException ignored) {
            // Readiness responses must never disclose URLs, credentials, or driver details.
        }
        return new PlatformStatusResponse.DiagnosticCheck(
                "persistence",
                "Persistence",
                "blocked",
                "blocked",
                "Configured persistence is unavailable.",
                "Restore the configured local persistence dependency and retry readiness.");
    }
}
