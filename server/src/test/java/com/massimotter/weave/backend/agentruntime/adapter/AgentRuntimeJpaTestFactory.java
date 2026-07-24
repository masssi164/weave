package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.testing.JpaTestDatabase;
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

    public record Context(
            JpaRuntimeCellRepository cells,
            JpaRuntimeCommandRepository commands,
            JpaRuntimeProfileRepository profiles,
            JpaRuntimeGovernanceRepository governance) {
    }
}
