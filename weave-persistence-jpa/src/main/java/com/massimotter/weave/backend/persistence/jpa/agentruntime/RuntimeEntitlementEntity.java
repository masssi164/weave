package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weave_agent_runtime_entitlements")
public class RuntimeEntitlementEntity {
  @Id private UUID recordId;

  @Column(nullable = false, unique = true)
  private String entitlementRef;

  @Column(nullable = false, unique = true, length = 71)
  private String entitlementRevision;

  @Column(nullable = false)
  private String organizationRef;

  @Column(nullable = false)
  private String personRef;

  @Column(nullable = false)
  private String memberIssuer;

  @Column(nullable = false)
  private String memberSubject;

  @Column(nullable = false)
  private String sourceProvider;

  @Column(nullable = false, length = 71)
  private String sourceGroupRef;

  @Column(nullable = false, length = 71)
  private String capabilityRevision;

  @Column(nullable = false)
  private String entitlementState;

  @Column(nullable = false)
  private Instant effectiveAt;

  @Column(nullable = false)
  private Instant lastObservedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private String revocationRef;
  private Instant revokedAt;

  @Column(nullable = false)
  private String auditRef;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected RuntimeEntitlementEntity() {}

  public RuntimeEntitlementEntity(
      UUID id,
      String ref,
      String revision,
      String org,
      String person,
      String issuer,
      String subject,
      String provider,
      String group,
      String capability,
      String state,
      Instant effective,
      Instant observed,
      Instant expires,
      String revocation,
      Instant revoked,
      String audit,
      Instant created,
      Instant updated) {
    recordId = id;
    entitlementRef = ref;
    entitlementRevision = revision;
    organizationRef = org;
    personRef = person;
    memberIssuer = issuer;
    memberSubject = subject;
    sourceProvider = provider;
    sourceGroupRef = group;
    capabilityRevision = capability;
    entitlementState = state;
    effectiveAt = effective;
    lastObservedAt = observed;
    expiresAt = expires;
    revocationRef = revocation;
    revokedAt = revoked;
    auditRef = audit;
    createdAt = created;
    updatedAt = updated;
  }

  public UUID recordId() {
    return recordId;
  }

  public String entitlementRef() {
    return entitlementRef;
  }

  public String entitlementRevision() {
    return entitlementRevision;
  }

  public String organizationRef() {
    return organizationRef;
  }

  public String personRef() {
    return personRef;
  }

  public String memberIssuer() {
    return memberIssuer;
  }

  public String memberSubject() {
    return memberSubject;
  }

  public String sourceProvider() {
    return sourceProvider;
  }

  public String sourceGroupRef() {
    return sourceGroupRef;
  }

  public String capabilityRevision() {
    return capabilityRevision;
  }

  public String entitlementState() {
    return entitlementState;
  }

  public Instant effectiveAt() {
    return effectiveAt;
  }

  public Instant lastObservedAt() {
    return lastObservedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public String revocationRef() {
    return revocationRef;
  }

  public Instant revokedAt() {
    return revokedAt;
  }

  public String auditRef() {
    return auditRef;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }

  public void observe(Instant observed, Instant expires, String audit, Instant now) {
    if (!"ENTITLED".equals(entitlementState))
      throw new IllegalStateException("a revoked entitlement activation cannot be replayed");
    if (observed.isAfter(lastObservedAt)) lastObservedAt = observed;
    if (expires.isAfter(expiresAt)) expiresAt = expires;
    auditRef = audit;
    updatedAt = now;
  }

  public void revoke(String ref, Instant effective, String audit, Instant now) {
    if ("REVOKED".equals(entitlementState) && !ref.equals(revocationRef))
      throw new IllegalStateException("entitlement was revoked by different evidence");
    entitlementState = "REVOKED";
    revocationRef = ref;
    revokedAt = effective;
    auditRef = audit;
    updatedAt = now;
  }
}
