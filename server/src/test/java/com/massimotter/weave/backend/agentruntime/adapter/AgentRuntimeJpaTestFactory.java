package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeMemberBinding;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeWorkloadOwnership;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import java.time.Instant;
import javax.sql.DataSource;

/** Focused-test composition root for the typed ARC persistence adapters. */
public final class AgentRuntimeJpaTestFactory {

    private AgentRuntimeJpaTestFactory() {
    }

    public static Context create(DataSource dataSource) {
        RuntimeCellJpaRepository cellStore = JpaTestDatabase.repository(
                dataSource,
                RuntimeCellJpaRepository.class);
        JpaRuntimeCellRepository cells = JpaTestDatabase.transactional(
                dataSource,
                new JpaRuntimeCellRepository(cellStore));
        JpaRuntimeCommandRepository commands = new JpaRuntimeCommandRepository(
                JpaTestDatabase.repository(
                        dataSource,
                        RuntimeCommandJpaRepository.class),
                JpaTestDatabase.transactionManager(dataSource));
        JpaRuntimeProfileRepository profiles = JpaTestDatabase.transactional(
                dataSource,
                new JpaRuntimeProfileRepository(
                        JpaTestDatabase.repository(
                                dataSource,
                                RuntimeProfileJpaRepository.class),
                        JpaTestDatabase.repository(
                                dataSource,
                                RuntimeProfileSignatureJpaRepository.class),
                        cellStore));
        JpaRuntimeGovernanceRepository governance =
                new JpaRuntimeGovernanceRepository(
                        JpaTestDatabase.repository(
                                dataSource,
                                RuntimeEntitlementJpaRepository.class),
                        JpaTestDatabase.repository(
                                dataSource,
                                RuntimeRevocationJpaRepository.class),
                        JpaTestDatabase.repository(
                                dataSource,
                                RuntimeAuditCorrelationJpaRepository.class),
                        JpaTestDatabase.transactionManager(dataSource));
        return new Context(cells, commands, profiles, governance);
    }

    public static RuntimeEntitlementRef activateEntitlement(
            Context context,
            String organizationRef,
            String personRef,
            RuntimeMemberBinding memberBinding,
            Instant now) {
        RuntimeEntitlementObservation observation = new RuntimeEntitlementObservation(
                organizationRef,
                personRef,
                memberBinding,
                "keycloak",
                RuntimeWorkloadOwnership.fingerprint(
                        "organization-group:/capabilities/weaver"),
                RuntimeWorkloadOwnership.fingerprint(
                        "capability:agent-runtime.entitled"),
                now,
                now.plusSeconds(3_600));
        return context.governance().activate(
                observation,
                "test-entitlement:" + personRef,
                "audit:test-entitlement:" + personRef,
                now);
    }

    public record Context(
            JpaRuntimeCellRepository cells,
            JpaRuntimeCommandRepository commands,
            JpaRuntimeProfileRepository profiles,
            JpaRuntimeGovernanceRepository governance) {
    }
}
