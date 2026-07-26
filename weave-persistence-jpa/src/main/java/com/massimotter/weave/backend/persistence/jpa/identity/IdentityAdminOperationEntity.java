package com.massimotter.weave.backend.persistence.jpa.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_identity_admin_operations")
@IdClass(IdentityAdminOperationId.class)
public class IdentityAdminOperationEntity {
  @Id
  @Column(name = "organization_id", length = 255, nullable = false, updatable = false)
  private String organizationId;

  @Id
  @Column(name = "idempotency_key", length = 128, nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "operation_kind", length = 80, nullable = false, updatable = false)
  private String operationKind;

  @Column(name = "request_hash", length = 64, nullable = false, updatable = false)
  private String requestHash;

  @Column(name = "status", length = 20, nullable = false)
  private String status;

  @Column(name = "response_json", length = 16_384)
  private String responseJson;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected IdentityAdminOperationEntity() {}

  public IdentityAdminOperationEntity(
      String organizationId,
      String idempotencyKey,
      String operationKind,
      String requestHash,
      Instant now) {
    this.organizationId = organizationId;
    this.idempotencyKey = idempotencyKey;
    this.operationKind = operationKind;
    this.requestHash = requestHash;
    this.status = "pending";
    this.createdAt = now;
    this.updatedAt = now;
  }

  public void complete(String responseJson, Instant now) {
    this.status = "completed";
    this.responseJson = responseJson;
    this.updatedAt = now;
  }

  public String operationKind() {
    return operationKind;
  }

  public String requestHash() {
    return requestHash;
  }

  public String status() {
    return status;
  }

  public String responseJson() {
    return responseJson;
  }
}
