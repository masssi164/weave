package com.massimotter.weave.backend.chat.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatrixIdentityProjectionJpaRepository
    extends JpaRepository<MatrixIdentityProjectionJpaEntity, MatrixIdentityProjectionId> {
  java.util.List<MatrixIdentityProjectionJpaEntity>
      findByTenantIdAndIdentityIssuerAndMatrixUserIdGreaterThanOrderByMatrixUserId(
          String tenantId, String identityIssuer, String afterUserId,
          org.springframework.data.domain.Pageable page);
}
