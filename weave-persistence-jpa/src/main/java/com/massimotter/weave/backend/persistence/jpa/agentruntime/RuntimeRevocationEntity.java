package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weave_agent_runtime_revocations")
public class RuntimeRevocationEntity {
  @Id private UUID recordId;

  @Column(nullable = false, unique = true)
  private String revocationRef;

  @Column(nullable = false)
  private String organizationRef;

  @Column(nullable = false)
  private String personRef;

  @Column(nullable = false)
  private String reasonCode;

  @Column(nullable = false, length = 71)
  private String reasonRefHash;

  @Column(nullable = false, length = 71)
  private String actorRefHash;

  @Column(nullable = false)
  private Instant effectiveAt;

  @Column(nullable = false)
  private String entitlementRef;

  @Column(nullable = false, length = 71)
  private String entitlementRevision;

  @Column(nullable = false)
  private String cellRef;

  private String profileHash;

  @Column(nullable = false, length = 71)
  private String workloadRefHash;

  @Column(nullable = false)
  private String auditCorrelationRef;

  @Column(nullable = false)
  private Instant createdAt;

  protected RuntimeRevocationEntity() {}

  public RuntimeRevocationEntity(
      UUID id,
      String ref,
      String org,
      String person,
      String code,
      String reason,
      String actor,
      Instant effective,
      String entitlement,
      String revision,
      String cell,
      String profile,
      String workload,
      String audit,
      Instant created) {
    recordId = id;
    revocationRef = ref;
    organizationRef = org;
    personRef = person;
    reasonCode = code;
    reasonRefHash = reason;
    actorRefHash = actor;
    effectiveAt = effective;
    entitlementRef = entitlement;
    entitlementRevision = revision;
    cellRef = cell;
    profileHash = profile;
    workloadRefHash = workload;
    auditCorrelationRef = audit;
    createdAt = created;
  }

  public UUID recordId() {
    return recordId;
  }

  public String revocationRef() {
    return revocationRef;
  }

  public String organizationRef() {
    return organizationRef;
  }

  public String personRef() {
    return personRef;
  }

  public String reasonCode() {
    return reasonCode;
  }

  public String reasonRefHash() {
    return reasonRefHash;
  }

  public String actorRefHash() {
    return actorRefHash;
  }

  public Instant effectiveAt() {
    return effectiveAt;
  }

  public String entitlementRef() {
    return entitlementRef;
  }

  public String entitlementRevision() {
    return entitlementRevision;
  }

  public String cellRef() {
    return cellRef;
  }

  public String profileHash() {
    return profileHash;
  }

  public String workloadRefHash() {
    return workloadRefHash;
  }

  public String auditCorrelationRef() {
    return auditCorrelationRef;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
