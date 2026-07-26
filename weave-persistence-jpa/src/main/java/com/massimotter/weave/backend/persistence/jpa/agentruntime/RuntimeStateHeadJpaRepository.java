package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RuntimeStateHeadJpaRepository
    extends JpaRepository<RuntimeStateHeadEntity, String> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select head from RuntimeStateHeadEntity head where head.runtimeStateStoreRef = :storeRef")
  Optional<RuntimeStateHeadEntity> lockByStoreRef(@Param("storeRef") String storeRef);
}
