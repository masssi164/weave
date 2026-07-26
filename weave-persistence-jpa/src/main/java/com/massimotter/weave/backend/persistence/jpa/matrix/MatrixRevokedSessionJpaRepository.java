package com.massimotter.weave.backend.persistence.jpa.matrix;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MatrixRevokedSessionJpaRepository
    extends JpaRepository<MatrixRevokedSessionEntity, String> {

  boolean existsBySessionHashAndExpiresAtAfter(String sessionHash, Instant observedAt);

  long deleteByExpiresAtLessThanEqual(Instant observedAt);
}
