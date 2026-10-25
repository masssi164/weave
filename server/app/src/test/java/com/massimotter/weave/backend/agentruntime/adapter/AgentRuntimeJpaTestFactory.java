package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.security.workload.domain.WorkloadReferences;

import com.massimotter.weave.backend.security.workload.domain.WorkloadEntitlementObservation;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementRef;
import com.massimotter.weave.backend.security.workload.domain.MemberIdentityBinding;
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
            MemberIdentityBinding memberBinding,
            Instant now) {
        WorkloadEntitlementObservation observation = new WorkloadEntitlementObservation(
                organizationRef,
                personRef,
                memberBinding,
                "test-member",
                "keycloak",
                WorkloadReferences.fingerprint(
                        "organization-group:/capabilities/weaver"),
                WorkloadReferences.fingerprint(
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
