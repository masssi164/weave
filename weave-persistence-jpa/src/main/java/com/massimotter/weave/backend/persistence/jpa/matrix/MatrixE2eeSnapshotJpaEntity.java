package com.massimotter.weave.backend.persistence.jpa.matrix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "weave_matrix_e2ee_snapshots")
public class MatrixE2eeSnapshotJpaEntity {

  @Id
  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "sequence_value", nullable = false)
  private long sequence;

  @Column(name = "payload_json", nullable = false, length = Integer.MAX_VALUE)
  private String payloadJson;

  @Column(name = "updated_at_utc", nullable = false)
  private Instant updatedAt;

  protected MatrixE2eeSnapshotJpaEntity() {}

  public MatrixE2eeSnapshotJpaEntity(
      String tenantId, long sequence, String payloadJson, Instant updatedAt) {
    this.tenantId = tenantId;
    this.sequence = sequence;
    this.payloadJson = payloadJson;
    this.updatedAt = updatedAt;
  }

  public void replace(long sequence, String payloadJson, Instant updatedAt) {
    this.sequence = sequence;
    this.payloadJson = payloadJson;
    this.updatedAt = updatedAt;
  }

  public String tenantId() {
    return tenantId;
  }

  public long sequence() {
    return sequence;
  }

  public String payloadJson() {
    return payloadJson;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
