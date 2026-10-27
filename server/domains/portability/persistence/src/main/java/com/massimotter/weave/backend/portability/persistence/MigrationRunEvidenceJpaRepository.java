package com.massimotter.weave.backend.portability.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationRunEvidenceJpaRepository
    extends JpaRepository<MigrationRunEvidenceJpaEntity, MigrationRunEvidenceId> {}
