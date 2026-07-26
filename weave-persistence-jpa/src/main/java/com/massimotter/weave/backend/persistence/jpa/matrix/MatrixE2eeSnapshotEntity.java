package com.massimotter.weave.backend.persistence.jpa.matrix;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_matrix_e2ee_snapshots")
public class MatrixE2eeSnapshotEntity {

  @Id
  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Column(name = "sequence_value", nullable = false)
  private long sequence;

  @Column(name = "payload_json", nullable = false, length = Integer.MAX_VALUE)
  private String payloadJson;

  @Column(name = "updated_at_utc", nullable = false)
  private Instant updatedAt;

  protected MatrixE2eeSnapshotEntity() {}

  public MatrixE2eeSnapshotEntity(
      String tenantId, long sequence, String payloadJson, Instant updatedAt) {
    this.tenantId = tenantId;
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
