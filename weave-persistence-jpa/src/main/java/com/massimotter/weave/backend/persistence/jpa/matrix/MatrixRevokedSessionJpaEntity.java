package com.massimotter.weave.backend.persistence.jpa.matrix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_matrix_revoked_sessions")
public class MatrixRevokedSessionJpaEntity {

  @Id
  @Column(name = "session_hash", length = 64, nullable = false)
  private String sessionHash;

  @Column(name = "revoked_at_utc", nullable = false)
  private Instant revokedAt;

  @Column(name = "expires_at_utc", nullable = false)
  private Instant expiresAt;

  protected MatrixRevokedSessionJpaEntity() {}

  public MatrixRevokedSessionJpaEntity(
      String sessionHash, Instant revokedAt, Instant expiresAt) {
    this.sessionHash = sessionHash;
    this.revokedAt = revokedAt;
    this.expiresAt = expiresAt;
  }

  public String sessionHash() {
    return sessionHash;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }
}
