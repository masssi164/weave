package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeAuditCorrelationJpaRepository
    extends JpaRepository<RuntimeAuditCorrelationEntity, UUID> {
  Optional<RuntimeAuditCorrelationEntity> findByCorrelationRef(String ref);
}
