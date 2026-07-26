package com.massimotter.weave.backend.persistence.jpa.matrix;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatrixE2eeSnapshotJpaRepository
    extends JpaRepository<MatrixE2eeSnapshotEntity, String> {}
