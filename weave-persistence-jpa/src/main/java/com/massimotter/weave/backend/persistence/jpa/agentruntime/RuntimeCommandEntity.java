package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@IdClass(RuntimeCommandId.class)
@Table(name = "weave_agent_runtime_commands")
public class RuntimeCommandEntity {
  @Id private String organizationRef;
  @Id private String personRef;
  @Id private String idempotencyKey;

  @Column(nullable = false)
  private String command;

  @Column(nullable = false)
  private String status;

  @Column(nullable = false)
  private String cellRef;

  private Long runtimeVersion;

  @Column(nullable = false)
  private String auditRef;

  private String failureCode;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected RuntimeCommandEntity() {}

  public RuntimeCommandEntity(
      String organizationRef,
      String personRef,
      String idempotencyKey,
      String command,
      String status,
      String cellRef,
      Long runtimeVersion,
      String auditRef,
      String failureCode,
      Instant createdAt,
      Instant updatedAt) {
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.idempotencyKey = idempotencyKey;
    this.command = command;
    this.status = status;
    this.cellRef = cellRef;
    this.runtimeVersion = runtimeVersion;
    this.auditRef = auditRef;
    this.failureCode = failureCode;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public String organizationRef() {
    return organizationRef;
  }

  public String personRef() {
    return personRef;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public String command() {
    return command;
  }

  public String status() {
    return status;
  }

  public String cellRef() {
    return cellRef;
  }

  public Long runtimeVersion() {
    return runtimeVersion;
  }

  public String auditRef() {
    return auditRef;
  }

  public String failureCode() {
    return failureCode;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void complete(long version, Instant now) {
    status = "COMPLETED";
    runtimeVersion = version;
    failureCode = null;
    updatedAt = now;
  }

  public void fail(String code, Instant now) {
    status = "FAILED";
    failureCode = code;
    updatedAt = now;
  }
}
