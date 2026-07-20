package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import com.massimotter.weave.backend.model.PlatformStatusResponse;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;
import org.springframework.stereotype.Service;

@Service
public class LocalDependencyReadinessService {

    static final int PERSISTENCE_QUERY_TIMEOUT_SECONDS = 2;

    private final JdbcTemplate jdbcTemplate;
    private final RuntimeStateStore runtimeStateStore;
    private final AgentRuntimeWorkloadReconciliationService workloadReconciliation;

    public LocalDependencyReadinessService(
            Optional<JdbcTemplate> jdbcTemplate,
            Optional<DataSource> dataSource,
            Optional<RuntimeStateStore> runtimeStateStore,
            Optional<AgentRuntimeWorkloadReconciliationService> workloadReconciliation) {
        this.jdbcTemplate = jdbcTemplate.orElseGet(() -> dataSource.map(JdbcTemplate::new).orElse(null));
        this.runtimeStateStore = runtimeStateStore.orElse(null);
        this.workloadReconciliation = workloadReconciliation.orElse(null);
    }

    public List<PlatformStatusResponse.DiagnosticCheck> checks() {
        List<PlatformStatusResponse.DiagnosticCheck> checks = new ArrayList<>();
        if (jdbcTemplate != null) {
            checks.add(persistenceCheck());
        }
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
            Integer result = jdbcTemplate.execute((StatementCallback<Integer>) statement -> {
                statement.setQueryTimeout(PERSISTENCE_QUERY_TIMEOUT_SECONDS);
                try (ResultSet resultSet = statement.executeQuery("SELECT 1")) {
                    return resultSet.next() ? resultSet.getInt(1) : null;
                }
            });
            if (Integer.valueOf(1).equals(result)) {
                return new PlatformStatusResponse.DiagnosticCheck(
                        "persistence",
                        "Persistence",
                        "up",
                        "ready",
                        "Configured persistence is reachable.",
                        null);
            }
        } catch (RuntimeException ignored) {
            // Readiness responses must never disclose JDBC URLs, credentials, or driver details.
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
