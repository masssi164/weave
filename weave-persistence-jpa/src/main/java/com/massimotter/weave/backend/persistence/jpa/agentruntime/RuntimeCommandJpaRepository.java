package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RuntimeCommandJpaRepository
    extends JpaRepository<RuntimeCommandEntity, RuntimeCommandId> {}
