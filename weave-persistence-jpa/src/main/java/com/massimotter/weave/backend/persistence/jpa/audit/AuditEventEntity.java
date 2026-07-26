package com.massimotter.weave.backend.persistence.jpa.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(
    name = "weave_audit_events",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_weave_audit_events_idempotency",
            columnNames = {"tenant_id", "idempotency_key"}))
public class AuditEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "sequence_id")
  private Long sequenceId;

  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Column(name = "context_id", length = 255)
  private String contextId;

  @Column(name = "actor_ref", length = 255, nullable = false)
  private String actorRef;

  @Column(name = "source_ref", length = 255, nullable = false)
  private String sourceRef;

  @Column(name = "action", length = 120, nullable = false)
  private String action;

  @Column(name = "occurred_at_utc", nullable = false)
  private Instant occurredAt;

  @Column(name = "idempotency_key", length = 255, nullable = false)
  private String idempotencyKey;

  @Column(name = "redaction_level", length = 80, nullable = false)
  private String redactionLevel;

  @Column(name = "payload_json", nullable = false, length = Integer.MAX_VALUE)
  private String payloadJson;

  protected AuditEventEntity() {}

  public AuditEventEntity(
      String tenantId,
      String contextId,
      String actorRef,
      String sourceRef,
      String action,
      Instant occurredAt,
      String idempotencyKey,
      String redactionLevel,
      String payloadJson) {
    this.tenantId = tenantId;
    this.contextId = contextId;
    this.actorRef = actorRef;
    this.sourceRef = sourceRef;
    this.action = action;
    this.occurredAt = occurredAt;
    this.idempotencyKey = idempotencyKey;
    this.redactionLevel = redactionLevel;
    this.payloadJson = payloadJson;
  }

  public Long sequenceId() {
    return sequenceId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String contextId() {
    return contextId;
  }

  public String actorRef() {
    return actorRef;
  }

  public String sourceRef() {
    return sourceRef;
  }

  public String action() {
    return action;
  }

  public Instant occurredAt() {
    return occurredAt;
  }

  public String idempotencyKey() {
    return idempotencyKey;
  }

  public String redactionLevel() {
    return redactionLevel;
  }

  public String payloadJson() {
    return payloadJson;
  }
}
