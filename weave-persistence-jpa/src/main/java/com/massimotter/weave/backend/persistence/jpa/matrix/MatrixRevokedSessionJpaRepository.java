package com.massimotter.weave.backend.persistence.jpa.matrix;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MatrixRevokedSessionJpaRepository
    extends JpaRepository<MatrixRevokedSessionJpaEntity, String> {

  boolean existsBySessionHashAndExpiresAtAfter(String sessionHash, Instant observedAt);

  long deleteByExpiresAtLessThanEqual(Instant observedAt);

  @Modifying
  @Query("update MatrixRevokedSessionJpaEntity session set session.expiresAt = "
      + "case when session.expiresAt < :expiresAt then :expiresAt else session.expiresAt end "
      + "where session.sessionHash = :sessionHash")
  int extendExpiry(@Param("sessionHash") String sessionHash, @Param("expiresAt") Instant expiresAt);

  @Modifying
  @Query("update MatrixRevokedSessionJpaEntity session set "
      + "session.revokedAt = case when session.revokedAt < :revokedBefore then :revokedBefore else session.revokedAt end, "
      + "session.expiresAt = case when session.expiresAt < :expiresAt then :expiresAt else session.expiresAt end "
      + "where session.sessionHash = :memberHash")
  int advanceMemberCutoff(@Param("memberHash") String memberHash,
      @Param("revokedBefore") Instant revokedBefore, @Param("expiresAt") Instant expiresAt);
}
