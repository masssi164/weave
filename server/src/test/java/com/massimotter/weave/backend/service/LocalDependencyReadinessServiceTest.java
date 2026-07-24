package com.massimotter.weave.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class LocalDependencyReadinessServiceTest {

    @Test
    void delegatesThePersistenceProbeToTheSpringBootHealthIndicator() {
        PersistenceHealthProbe persistence = mock(PersistenceHealthProbe.class);
        when(persistence.ready()).thenReturn(true);
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                persistence, Optional.empty(), Optional.empty());

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("persistence");
            assertThat(check.status()).isEqualTo("up");
            assertThat(check.readiness()).isEqualTo("ready");
            assertThat(check.action()).isNull();
        });
    }

    @Test
    void redactsJpaFailureDetails() {
        PersistenceHealthProbe persistence = mock(PersistenceHealthProbe.class);
        when(persistence.ready())
                .thenThrow(new IllegalStateException(
                        "jdbc:postgresql://db.internal/weave?password=do-not-expose"));
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                persistence, Optional.empty(), Optional.empty());

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("persistence");
            assertThat(check.status()).isEqualTo("blocked");
            assertThat(check.readiness()).isEqualTo("blocked");
            assertThat(check.message()).isEqualTo("Configured persistence is unavailable.");
            assertThat(check.action())
                    .isEqualTo("Restore the configured local persistence dependency and retry readiness.");
            assertThat(check.toString())
                    .doesNotContain("db.internal", "password", "do-not-expose", "postgresql");
        });
    }

    @Test
    void reportsWorkloadIdentityReconciliationAsReadyOnlyAfterConvergence() {
        AgentRuntimeWorkloadReconciliationService reconciliation =
                mock(AgentRuntimeWorkloadReconciliationService.class);
        RuntimeWorkloadReconciliationReport report = mock(RuntimeWorkloadReconciliationReport.class);
        when(reconciliation.supportSafeSnapshot()).thenReturn(report);
        when(report.state()).thenReturn(RuntimeWorkloadReconciliationReport.State.CONVERGED);
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                readyPersistence(), Optional.empty(), Optional.of(reconciliation));

        assertThat(service.checks()).filteredOn(check ->
                        "agent-runtime-workload-identities".equals(check.key()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.readiness()).isEqualTo("ready");
                    assertThat(check.action()).isNull();
                });
    }

    @Test
    void blocksReadinessWhileWorkloadIdentityReconciliationIsNotConverged() {
        AgentRuntimeWorkloadReconciliationService reconciliation =
                mock(AgentRuntimeWorkloadReconciliationService.class);
        RuntimeWorkloadReconciliationReport report = mock(RuntimeWorkloadReconciliationReport.class);
        when(reconciliation.supportSafeSnapshot()).thenReturn(report);
        when(report.state()).thenReturn(RuntimeWorkloadReconciliationReport.State.BLOCKED);
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                readyPersistence(), Optional.empty(), Optional.of(reconciliation));

        assertThat(service.checks()).filteredOn(check ->
                        "agent-runtime-workload-identities".equals(check.key()))
                .singleElement()
                .satisfies(check -> {
                    assertThat(check.readiness()).isEqualTo("blocked");
                    assertThat(check.toString())
                            .doesNotContain("credentialref", "weaver-cell-", "keycloak/clients");
                });
    }

    private PersistenceHealthProbe readyPersistence() {
        PersistenceHealthProbe persistence = mock(PersistenceHealthProbe.class);
        when(persistence.ready()).thenReturn(true);
        return persistence;
    }
}
