package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_agent_runtime_profiles")
public class RuntimeProfileEntity {
  @Id
  @Column(length = 71)
  private String profileHash;

  @Column(nullable = false, unique = true)
  private String profileId;

  @Column(nullable = false)
  private String cellRef;

  @Column(nullable = false)
  private String organizationRef;

  @Column(nullable = false)
  private String personRef;

  @Column(nullable = false, length = Integer.MAX_VALUE)
  private String payload;

  @Column(nullable = false)
  private String selectedKeyId;

  @Column(nullable = false)
  private Instant issuedAt;

  @Column(nullable = false)
  private Instant expiresAt;

  private Instant revokedAt;
  private String revocationCode;

  @Column(nullable = false)
  private Instant createdAt;

  protected RuntimeProfileEntity() {}

  public RuntimeProfileEntity(
      String hash,
      String id,
      String cell,
      String org,
      String person,
      String payload,
      String key,
      Instant issued,
      Instant expires,
      Instant created) {
    profileHash = hash;
    profileId = id;
    cellRef = cell;
    organizationRef = org;
    personRef = person;
    this.payload = payload;
    selectedKeyId = key;
    issuedAt = issued;
    expiresAt = expires;
    createdAt = created;
  }

  public String profileHash() {
    return profileHash;
  }

  public String profileId() {
    return profileId;
  }

  public String cellRef() {
    return cellRef;
  }

  public String organizationRef() {
    return organizationRef;
  }

  public String personRef() {
    return personRef;
  }

  public String payload() {
    return payload;
  }

  public String selectedKeyId() {
    return selectedKeyId;
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

  public void select(String key) {
    if (revokedAt != null)
      throw new IllegalStateException("revoked RuntimeProfile cannot select a signing key");
    selectedKeyId = key;
  }

  public void revoke(String code, Instant now) {
    if (revokedAt == null) {
      revokedAt = now;
      revocationCode = code;
    }
  }
}
