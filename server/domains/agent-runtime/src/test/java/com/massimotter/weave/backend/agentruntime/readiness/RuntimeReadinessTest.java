package com.massimotter.weave.backend.agentruntime.readiness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import com.massimotter.weave.backend.agentruntime.port.RuntimeStateStore;
import org.junit.jupiter.api.Test;

class RuntimeReadinessTest {
    @Test
    void reportsWorkloadIdentityReconciliationAsReadyOnlyAfterConvergence() {
        var reconciliation = mock(AgentRuntimeWorkloadReconciliationService.class);
        var report = mock(RuntimeWorkloadReconciliationReport.class);
        when(reconciliation.supportSafeSnapshot()).thenReturn(report);
        when(report.state()).thenReturn(RuntimeWorkloadReconciliationReport.State.CONVERGED);
        var contribution = new RuntimeWorkloadReadiness(reconciliation);
        var check = contribution.check();
        assertThat(check.id()).isEqualTo("agent-runtime-workload-identities");
        assertThat(check.readiness()).isEqualTo("ready");
        assertThat(check.remediation()).isNull();
    }

    @Test
    void blocksReadinessWhileWorkloadIdentityReconciliationIsNotConverged() {
        var reconciliation = mock(AgentRuntimeWorkloadReconciliationService.class);
        var report = mock(RuntimeWorkloadReconciliationReport.class);
        when(reconciliation.supportSafeSnapshot()).thenReturn(report);
        when(report.state()).thenReturn(RuntimeWorkloadReconciliationReport.State.BLOCKED);
        var check = new RuntimeWorkloadReadiness(reconciliation).check();
        assertThat(check.id()).isEqualTo("agent-runtime-workload-identities");
        assertThat(check.readiness()).isEqualTo("blocked");
        assertThat(check.toString()).doesNotContain("credentialref", "weaver-cell-", "keycloak/clients");
    }

    @Test
    void contributionConstructionDoesNotProbeExternalStateOrReconciliation() {
        var store = mock(RuntimeStateStore.class);
        var reconciliation = mock(AgentRuntimeWorkloadReconciliationService.class);
        new RuntimeStateReadiness(store);
        new RuntimeWorkloadReadiness(reconciliation);
        verifyNoInteractions(store, reconciliation);
    }

    @Test
    void stateReadinessPreservesReadyAndBlockedSupportSafeProjections() {
        var store = mock(RuntimeStateStore.class);
        var readiness = mock(RuntimeStateStore.StoreReadiness.class);
        when(store.readiness()).thenReturn(readiness);
        when(readiness.ready()).thenReturn(true, false);
        var contribution = new RuntimeStateReadiness(store);
        assertThat(contribution.check().readiness()).isEqualTo("ready");
        var blocked = contribution.check();
        assertThat(blocked.id()).isEqualTo("agent-runtime-state");
        assertThat(blocked.status()).isEqualTo("blocked");
        assertThat(blocked.remediation())
                .isEqualTo("Restore and reconcile the runtime-state database and wrapping-key SecretRef set.");
    }
}
