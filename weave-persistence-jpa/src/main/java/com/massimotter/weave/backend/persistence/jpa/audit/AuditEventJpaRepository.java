package com.massimotter.weave.backend.persistence.jpa.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventJpaEntity, Long> {
  Optional<AuditEventJpaEntity> findByTenantIdAndIdempotencyKey(
      String tenantId, String idempotencyKey);

  List<AuditEventJpaEntity> findAllByOrderBySequenceIdAsc();
}
