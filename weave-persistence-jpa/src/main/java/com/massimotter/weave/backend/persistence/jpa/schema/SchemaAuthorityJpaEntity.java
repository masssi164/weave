package com.massimotter.weave.backend.persistence.jpa.schema;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Completed code-first schema authority marker for one pre-release persistence epoch. */
@Entity
@Table(name = "weave_schema_authority")
public class SchemaAuthorityJpaEntity {

  @Id
  @Column(name = "epoch", length = 80, nullable = false, updatable = false)
  private String epoch;

  @Column(name = "relational_model_id", length = 160, nullable = false)
  private String relationalModelId;

  @Column(name = "candidate_commit", length = 40, nullable = false)
  private String candidateCommit;

  @Column(name = "catalog_fingerprint", length = 64, nullable = false)
  private String catalogFingerprint;

  @Column(name = "completed_at_utc", nullable = false)
  private Instant completedAt;

  protected SchemaAuthorityJpaEntity() {}

  public SchemaAuthorityJpaEntity(
      String epoch,
      String relationalModelId,
      String candidateCommit,
      String catalogFingerprint,
      Instant completedAt) {
    this.epoch = epoch;
    this.relationalModelId = relationalModelId;
    this.candidateCommit = candidateCommit;
    this.catalogFingerprint = catalogFingerprint;
    this.completedAt = completedAt;
  }

  public String epoch() {
    return epoch;
  }

  public String relationalModelId() {
    return relationalModelId;
  }

  public String candidateCommit() {
    return candidateCommit;
  }

  public String catalogFingerprint() {
    return catalogFingerprint;
  }

  public Instant completedAt() {
    return completedAt;
  }
}
