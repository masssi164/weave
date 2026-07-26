package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@IdClass(RuntimeStateDeletionId.class)
@Table(name = "weave_agent_runtime_state_deletions")
public class RuntimeStateDeletionEntity {
  @Id private String organizationRef;
  @Id private String personRef;
  @Id private String idempotencyKey;
  private String cellRef;
  private String runtimeStateStoreRef;
  private long deletedGenerationCount;
  private String auditRef;
  private Instant completedAt;

  protected RuntimeStateDeletionEntity() {}

  public RuntimeStateDeletionEntity(
      String organizationRef,
      String personRef,
      String idempotencyKey,
      String cellRef,
      String storeRef,
      long deletedCount,
      String auditRef,
      Instant completedAt) {
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.idempotencyKey = idempotencyKey;
    this.cellRef = cellRef;
    this.runtimeStateStoreRef = storeRef;
    this.deletedGenerationCount = deletedCount;
    this.auditRef = auditRef;
    this.completedAt = completedAt;
  }

  public String cellRef() {
    return cellRef;
  }

  public String runtimeStateStoreRef() {
    return runtimeStateStoreRef;
  }
}
