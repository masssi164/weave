package com.massimotter.weave.backend.persistence.jpa.matrix;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatrixIdentityProjectionJpaRepository
    extends JpaRepository<MatrixIdentityProjectionEntity, MatrixIdentityProjectionId> {}
