package com.massimotter.weave.backend.persistence.jpa.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "weave_identity_provisioning_intents",
    indexes =
        @Index(
            name = "weave_identity_provisioning_pending_email",
            columnList = "tenant_id,organization_id,invited_email_sha256,status"))
public class ProvisioningIntentJpaEntity {

  @Id
  @Column(name = "intent_id", nullable = false)
  private UUID intentId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Column(name = "organization_id", length = 160, nullable = false)
  private String organizationId;

  @Column(name = "invited_email", length = 320, nullable = false)
  private String invitedEmail;

  @Column(name = "invited_email_sha256", length = 64, nullable = false)
  private String invitedEmailSha256;

  @Column(name = "requested_role", length = 32, nullable = false)
  private String requestedRole;

  @Column(name = "provider_invitation_id", length = 200, unique = true)
  private String providerInvitationId;

  @Column(name = "invited_by_issuer", length = 500, nullable = false)
  private String invitedByIssuer;

  @Column(name = "invited_by_subject", length = 255, nullable = false)
  private String invitedBySubject;

  @Column(name = "audit_correlation", length = 255, nullable = false)
  private String auditCorrelation;

  @Column(name = "status", length = 32, nullable = false)
  private String status;

  @Column(name = "applied_subject", length = 255)
  private String appliedSubject;

  @Column(name = "failure_code", length = 100)
  private String failureCode;

  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ProvisioningIntentJpaEntity() {}

  public ProvisioningIntentJpaEntity(
      UUID intentId,
      String tenantId,
      String organizationId,
      String invitedEmail,
      String invitedEmailSha256,
      String requestedRole,
      String providerInvitationId,
      String invitedByIssuer,
      String invitedBySubject,
      String auditCorrelation,
      String status,
      String appliedSubject,
      String failureCode,
      Instant expiresAt,
      Instant createdAt,
      Instant updatedAt) {
    this.intentId = intentId;
    this.tenantId = tenantId;
    this.organizationId = organizationId;
    this.invitedEmail = invitedEmail;
    this.invitedEmailSha256 = invitedEmailSha256;
    this.requestedRole = requestedRole;
    this.providerInvitationId = providerInvitationId;
    this.invitedByIssuer = invitedByIssuer;
    this.invitedBySubject = invitedBySubject;
    this.auditCorrelation = auditCorrelation;
    this.status = status;
    this.appliedSubject = appliedSubject;
    this.failureCode = failureCode;
    this.expiresAt = expiresAt;
    this.createdAt = createdAt;
    this.updatedAt = updatedAt;
  }

  public void updateMutableState(
      String providerInvitationId,
      String status,
      String appliedSubject,
      String failureCode,
      Instant updatedAt) {
    if (this.providerInvitationId != null
        && !this.providerInvitationId.equals(providerInvitationId)) {
      throw new IllegalStateException("provisioning intent provider binding is immutable");
    }
    if (updatedAt.isBefore(this.updatedAt)) {
      throw new IllegalStateException("provisioning intent update time cannot move backwards");
    }
    this.providerInvitationId = providerInvitationId;
    this.status = status;
    this.appliedSubject = appliedSubject;
    this.failureCode = failureCode;
    this.updatedAt = updatedAt;
  }

  public UUID intentId() {
    return intentId;
  }

  public String tenantId() {
    return tenantId;
  }

  public String organizationId() {
    return organizationId;
  }

  public String invitedEmail() {
    return invitedEmail;
  }

  public String invitedEmailSha256() {
    return invitedEmailSha256;
  }

  public String requestedRole() {
    return requestedRole;
  }

  public String providerInvitationId() {
    return providerInvitationId;
  }

  public String invitedByIssuer() {
    return invitedByIssuer;
  }

  public String invitedBySubject() {
    return invitedBySubject;
  }

  public String auditCorrelation() {
    return auditCorrelation;
  }

  public String status() {
    return status;
  }

  public String appliedSubject() {
    return appliedSubject;
  }

  public String failureCode() {
    return failureCode;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant createdAt() {
    return createdAt;
  }

  public Instant updatedAt() {
    return updatedAt;
  }
}
