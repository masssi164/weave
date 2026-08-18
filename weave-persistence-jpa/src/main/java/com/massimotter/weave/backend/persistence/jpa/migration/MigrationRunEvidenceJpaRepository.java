package com.massimotter.weave.backend.persistence.jpa.migration;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MigrationRunEvidenceJpaRepository
    extends JpaRepository<MigrationRunEvidenceJpaEntity, MigrationRunEvidenceId> {}
