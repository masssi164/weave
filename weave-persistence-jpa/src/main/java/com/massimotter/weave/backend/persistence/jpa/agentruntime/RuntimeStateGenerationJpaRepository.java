package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeStateGenerationJpaRepository
    extends JpaRepository<RuntimeStateGenerationEntity, String> {
  Optional<RuntimeStateGenerationEntity> findByRuntimeStateStoreRefAndIdempotencyKey(
      String storeRef, String idempotencyKey);

  List<RuntimeStateGenerationEntity> findByRuntimeStateStoreRefOrderByGenerationAsc(
      String storeRef);

  long countByRuntimeStateStoreRef(String storeRef);
}
