package com.massimotter.weave.backend.persistence.jpa.audit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditEventJpaRepository extends JpaRepository<AuditEventEntity, Long> {
  Optional<AuditEventEntity> findByTenantIdAndIdempotencyKey(
      String tenantId, String idempotencyKey);

  List<AuditEventEntity> findAllByOrderBySequenceIdAsc();
}
