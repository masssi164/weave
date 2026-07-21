package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.agentruntime.application.AgentRuntimeWorkloadReconciliationService;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadReconciliationReport;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Optional;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.StatementCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalDependencyReadinessServiceTest {

    @Test
    void omitsPersistenceCheckWhenJdbcIsNotConfigured() {
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());

        assertThat(service.checks()).isEmpty();
    }

    @Test
    void executesBoundedLightweightQueryWhenJdbcIsConfigured() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        Statement statement = mock(Statement.class);
        ResultSet resultSet = mock(ResultSet.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.createStatement()).thenReturn(statement);
        when(statement.executeQuery("SELECT 1")).thenReturn(resultSet);
        when(resultSet.next()).thenReturn(true);
        when(resultSet.getInt(1)).thenReturn(1);

        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.empty(), Optional.of(dataSource), Optional.empty(), Optional.empty());

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("persistence");
            assertThat(check.status()).isEqualTo("up");
            assertThat(check.readiness()).isEqualTo("ready");
            assertThat(check.action()).isNull();
        });
        verify(statement).setQueryTimeout(LocalDependencyReadinessService.PERSISTENCE_QUERY_TIMEOUT_SECONDS);
        verify(statement).executeQuery("SELECT 1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void redactsJdbcFailureDetails() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.execute(any(StatementCallback.class)))
                .thenThrow(new DataAccessResourceFailureException(
                        "jdbc:postgresql://db.internal/weave?password=do-not-expose"));
        LocalDependencyReadinessService service = new LocalDependencyReadinessService(
                Optional.of(jdbcTemplate), Optional.empty(), Optional.empty(), Optional.empty());

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
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(reconciliation));

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("agent-runtime-workload-identities");
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
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.of(reconciliation));

        assertThat(service.checks()).singleElement().satisfies(check -> {
            assertThat(check.key()).isEqualTo("agent-runtime-workload-identities");
            assertThat(check.readiness()).isEqualTo("blocked");
            assertThat(check.toString()).doesNotContain("credentialref", "weaver-cell-", "keycloak/clients");
        });
    }
}
