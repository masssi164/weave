package com.massimotter.weave.backend.persistence.jpa.security;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(
    name = "weave_device_credentials",
    indexes =
        @Index(
            name = "idx_weave_device_credentials_principal",
            columnList = "domain,principal_ref,issued_at_utc"))
public class DeviceCredentialJpaEntity {

  @Id
  @Column(name = "credential_id", length = 160, nullable = false)
  private String credentialId;

  @Column(name = "domain", length = 40, nullable = false)
  private String domain;

  @Column(name = "tenant_id", length = 160, nullable = false)
  private String tenantId;

  @Column(name = "principal_ref", length = 255, nullable = false)
  private String principalRef;

  @Column(name = "subject_ref", length = 255, nullable = false)
  private String subjectRef;

  @Column(name = "username", length = 255, nullable = false)
  private String username;

  @Column(name = "client_type", length = 80, nullable = false)
  private String clientType;

  @Column(name = "label", length = 255, nullable = false)
  private String label;

  @Column(name = "capabilities_json", nullable = false, length = Integer.MAX_VALUE)
  private String capabilitiesJson;

  @Column(name = "secret_hash", nullable = false, length = Integer.MAX_VALUE)
  private String secretHash;

  @Column(name = "issued_at_utc", nullable = false)
  private Instant issuedAt;

  @Column(name = "expires_at_utc", nullable = false)
  private Instant expiresAt;

  @Column(name = "revoked_at_utc")
  private Instant revokedAt;

  protected DeviceCredentialJpaEntity() {}

  public DeviceCredentialJpaEntity(
      String credentialId,
      String domain,
      String tenantId,
      String principalRef,
      String subjectRef,
      String username,
      String clientType,
      String label,
      String capabilitiesJson,
      String secretHash,
      Instant issuedAt,
      Instant expiresAt,
      Instant revokedAt) {
    this.credentialId = credentialId;
    this.domain = domain;
    this.tenantId = tenantId;
    this.principalRef = principalRef;
    this.subjectRef = subjectRef;
    this.username = username;
    this.clientType = clientType;
    this.label = label;
    this.capabilitiesJson = capabilitiesJson;
    this.secretHash = secretHash;
    this.issuedAt = issuedAt;
    this.expiresAt = expiresAt;
    this.revokedAt = revokedAt;
  }

  public String credentialId() {
    return credentialId;
  }

  public String domain() {
    return domain;
  }

  public String tenantId() {
    return tenantId;
  }

  public String principalRef() {
    return principalRef;
  }

  public String subjectRef() {
    return subjectRef;
  }

  public String username() {
    return username;
  }

  public String clientType() {
    return clientType;
  }

  public String label() {
    return label;
  }

  public String capabilitiesJson() {
    return capabilitiesJson;
  }

  public String secretHash() {
    return secretHash;
  }

  public Instant issuedAt() {
    return issuedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }

  public Instant revokedAt() {
    return revokedAt;
  }
}
