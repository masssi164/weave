package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeAuditCorrelationJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCellJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeCommandJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeEntitlementJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeProfileSignatureJpaRepository;
import com.massimotter.weave.backend.persistence.jpa.agentruntime.RuntimeRevocationJpaRepository;
import com.massimotter.weave.backend.testing.JpaTestDatabase;
import javax.sql.DataSource;

public final class AgentRuntimeJpaTestFactory {
  private AgentRuntimeJpaTestFactory() {}

  public static Context create(DataSource dataSource) {
    RuntimeCellJpaRepository cellStore =
        JpaTestDatabase.repository(dataSource, RuntimeCellJpaRepository.class);
    JpaRuntimeCellRepository cells =
        JpaTestDatabase.transactional(dataSource, new JpaRuntimeCellRepository(cellStore));
    JpaRuntimeCommandRepository commands =
        JpaTestDatabase.transactional(
            dataSource,
            new JpaRuntimeCommandRepository(
                JpaTestDatabase.repository(dataSource, RuntimeCommandJpaRepository.class)));
    JpaRuntimeProfileRepository profiles =
        JpaTestDatabase.transactional(
            dataSource,
            new JpaRuntimeProfileRepository(
                JpaTestDatabase.repository(dataSource, RuntimeProfileJpaRepository.class),
                JpaTestDatabase.repository(dataSource, RuntimeProfileSignatureJpaRepository.class),
                cellStore));
    JpaRuntimeGovernanceRepository governance =
        JpaTestDatabase.transactional(
            dataSource,
            new JpaRuntimeGovernanceRepository(
                JpaTestDatabase.repository(dataSource, RuntimeEntitlementJpaRepository.class),
                JpaTestDatabase.repository(dataSource, RuntimeRevocationJpaRepository.class),
                JpaTestDatabase.repository(
                    dataSource, RuntimeAuditCorrelationJpaRepository.class)));
    return new Context(cells, commands, profiles, governance);
  }

  public record Context(
      JpaRuntimeCellRepository cells,
      JpaRuntimeCommandRepository commands,
      JpaRuntimeProfileRepository profiles,
      JpaRuntimeGovernanceRepository governance) {}
}
