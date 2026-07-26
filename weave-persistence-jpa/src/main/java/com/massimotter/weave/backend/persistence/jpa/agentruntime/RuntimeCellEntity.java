package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "weave_agent_runtime_cells")
public class RuntimeCellEntity {
  @Id private UUID recordId;

  @Column(nullable = false)
  private String organizationRef;

  @Column(nullable = false)
  private String personRef;

  @Column(nullable = false)
  private String memberIssuer;

  @Column(nullable = false)
  private String memberSubject;

  @Column(nullable = false, unique = true)
  private String cellRef;

  @Column(nullable = false)
  private String workloadIssuer;

  @Column(nullable = false)
  private String workloadSubject;

  @Column(nullable = false)
  private String workloadClientId;

  @Column(nullable = false)
  private String workloadAuthenticationMethod;

  @Column(nullable = false, length = 1000)
  private String workloadCredentialRef;

  @Column(nullable = false)
  private String entitlementState;

  @Column(nullable = false)
  private String entitlementRevision;

  @Column(nullable = false)
  private String desiredState;

  @Column(nullable = false)
  private String observedState;

  private String runtimeProfileId;
  private String runtimeProfileHash;

  @Column(nullable = false)
  private String workspaceRevision;

  @Column(nullable = false, length = 1000)
  private String workspaceManifestRef;

  @Column(nullable = false, length = 1000)
  private String runtimeStateStoreRef;

  @Column(nullable = false)
  private long fencingEpoch;

  private UUID leaseId;
  private Instant leaseExpiresAt;
  @Version private long version;

  @Column(nullable = false)
  private String auditRef;

  @Column(nullable = false)
  private Instant createdAt;

  @Column(nullable = false)
  private Instant updatedAt;

  protected RuntimeCellEntity() {}

  public RuntimeCellEntity(
      UUID recordId,
      String organizationRef,
      String personRef,
      String memberIssuer,
      String memberSubject,
      String cellRef,
      String workloadIssuer,
      String workloadSubject,
      String workloadClientId,
      String workloadAuthenticationMethod,
      String workloadCredentialRef,
      String entitlementState,
      String entitlementRevision,
      String desiredState,
      String observedState,
      String runtimeProfileId,
      String runtimeProfileHash,
      String workspaceRevision,
      String workspaceManifestRef,
      String runtimeStateStoreRef,
      long fencingEpoch,
      UUID leaseId,
      Instant leaseExpiresAt,
      long version,
      String auditRef,
      Instant createdAt,
      Instant updatedAt) {
    this.recordId = recordId;
    this.organizationRef = organizationRef;
    this.personRef = personRef;
    this.memberIssuer = memberIssuer;
    this.memberSubject = memberSubject;
    this.cellRef = cellRef;
    this.workloadIssuer = workloadIssuer;
    this.workloadSubject = workloadSubject;
    this.workloadClientId = workloadClientId;
    this.workloadAuthenticationMethod = workloadAuthenticationMethod;
    this.workloadCredentialRef = workloadCredentialRef;
    this.entitlementState = entitlementState;
    this.entitlementRevision = entitlementRevision;
    this.desiredState = desiredState;
    this.observedState = observedState;
    this.runtimeProfileId = runtimeProfileId;
    this.runtimeProfileHash = runtimeProfileHash;
    this.workspaceRevision = workspaceRevision;
    this.workspaceManifestRef = workspaceManifestRef;
    this.runtimeStateStoreRef = runtimeStateStoreRef;
    this.fencingEpoch = fencingEpoch;
    this.leaseId = leaseId;
    this.leaseExpiresAt = leaseExpiresAt;
    this.version = version;
    this.auditRef = auditRef;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public UUID recordId() {
    return recordId;
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

  public String cellRef() {
    return cellRef;
  }

  public String workloadIssuer() {
    return workloadIssuer;
  }

  public String workloadSubject() {
    return workloadSubject;
  }

  public String workloadClientId() {
    return workloadClientId;
  }

  public String workloadAuthenticationMethod() {
    return workloadAuthenticationMethod;
  }

  public String workloadCredentialRef() {
    return workloadCredentialRef;
  }

  public String entitlementState() {
    return entitlementState;
  }

  public String entitlementRevision() {
    return entitlementRevision;
  }

  public String desiredState() {
    return desiredState;
  }

  public String observedState() {
    return observedState;
  }

  public String runtimeProfileId() {
    return runtimeProfileId;
  }

  public String runtimeProfileHash() {
    return runtimeProfileHash;
  }

  public String workspaceRevision() {
    return workspaceRevision;
  }

  public String workspaceManifestRef() {
    return workspaceManifestRef;
  }

  public String runtimeStateStoreRef() {
    return runtimeStateStoreRef;
  }

  public long fencingEpoch() {
    return fencingEpoch;
  }

  public UUID leaseId() {
    return leaseId;
  }

  public Instant leaseExpiresAt() {
    return leaseExpiresAt;
  }

  public long version() {
    return version;
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

  public void acquireLease(UUID leaseId, Instant expiresAt, Instant now) {
    this.leaseId = leaseId;
    this.leaseExpiresAt = expiresAt;
    this.fencingEpoch++;
    this.updatedAt = now;
  }

  public void renewLease(Instant expiresAt, Instant now) {
    this.leaseExpiresAt = expiresAt;
    this.updatedAt = now;
  }

  public void observe(String state, String auditRef, Instant now) {
    this.observedState = state;
    this.auditRef = auditRef;
    this.updatedAt = now;
  }

  public void bindEntitlement(String revision, String auditRef, Instant now) {
    entitlementState = "ENTITLED";
    entitlementRevision = revision;
    desiredState = "PROVISIONING";
    runtimeProfileId = null;
    runtimeProfileHash = null;
    leaseId = null;
    leaseExpiresAt = null;
    fencingEpoch++;
    this.auditRef = auditRef;
    updatedAt = now;
  }

  public void transitionDesiredState(String state, String auditRef, Instant now) {
    desiredState = state;
    this.auditRef = auditRef;
    updatedAt = now;
  }

  public void revoke(String revision, String auditRef, Instant now) {
    entitlementState = "REVOKED";
    entitlementRevision = revision;
    desiredState = "REVOKING";
    leaseId = null;
    leaseExpiresAt = null;
    fencingEpoch++;
    this.auditRef = auditRef;
    updatedAt = now;
  }

  public void activateProfile(String profileId, String profileHash, Instant now) {
    this.runtimeProfileId = profileId;
    this.runtimeProfileHash = profileHash;
    this.updatedAt = now;
  }
}
