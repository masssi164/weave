package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeRevocationJpaRepository
    extends JpaRepository<RuntimeRevocationEntity, UUID> {
  Optional<RuntimeRevocationEntity> findByRevocationRef(String ref);
}
