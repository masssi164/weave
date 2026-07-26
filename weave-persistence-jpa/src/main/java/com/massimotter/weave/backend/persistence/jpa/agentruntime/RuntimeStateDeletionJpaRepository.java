package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeStateDeletionJpaRepository
    extends JpaRepository<RuntimeStateDeletionEntity, RuntimeStateDeletionId> {}
