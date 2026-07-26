package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "weave_agent_runtime_state_heads")
public class RuntimeStateHeadEntity {
  @Id
  @Column(name = "runtime_state_store_ref", length = 1000, nullable = false, updatable = false)
  private String runtimeStateStoreRef;

  @Column(name = "organization_ref", nullable = false, updatable = false)
  private String organizationRef;

  @Column(name = "person_ref", nullable = false, updatable = false)
  private String personRef;

  @Column(name = "cell_ref", nullable = false, updatable = false)
  private String cellRef;

  @Column(name = "current_generation", nullable = false)
  private long currentGeneration;

  @Column(name = "current_generation_ref", length = 81)
  private String currentGenerationRef;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "audit_ref", nullable = false)
  private String auditRef;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected RuntimeStateHeadEntity() {}

  public RuntimeStateHeadEntity(
      String storeRef,
      String organizationRef,
      String personRef,
      String cellRef,
      String auditRef,
      Instant now) {
    this.runtimeStateStoreRef = storeRef;
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.cellRef = cellRef;
    this.auditRef = auditRef;
    this.createdAt = now;
    this.updatedAt = now;
  }

  public String runtimeStateStoreRef() {
    return runtimeStateStoreRef;
  }

  public String organizationRef() {
    return organizationRef;
  }

  public String personRef() {
    return personRef;
  }

  public String cellRef() {
    return cellRef;
  }

  public long currentGeneration() {
    return currentGeneration;
  }

  public String currentGenerationRef() {
    return currentGenerationRef;
  }

  public void advance(
      long expected, long next, String generationRef, String auditRef, Instant now) {
    if (currentGeneration != expected || next != currentGeneration + 1) {
      throw new IllegalStateException("stale-runtime-state-head");
    }
    currentGeneration = next;
    currentGenerationRef = generationRef;
    this.auditRef = auditRef;
    updatedAt = now;
  }

  public void clear(String auditRef, Instant now) {
    currentGeneration = 0;
    currentGenerationRef = null;
    this.auditRef = auditRef;
    updatedAt = now;
  }
}
