package com.massimotter.weave.backend.persistence.jpa.migration;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "weave_migration_run_evidence")
public class MigrationRunEvidenceJpaEntity {

  @EmbeddedId private MigrationRunEvidenceId id;

  @Column(name = "lifecycle", length = 80, nullable = false)
  private String lifecycle;

  @Column(name = "object_counts_json", nullable = false, length = Integer.MAX_VALUE)
  private String objectCountsJson;

  @Column(name = "content_hashes_json", nullable = false, length = Integer.MAX_VALUE)
  private String contentHashesJson;

  @Column(name = "audit_refs_json", nullable = false, length = Integer.MAX_VALUE)
  private String auditRefsJson;

  @Column(name = "artifact_refs_json", nullable = false, length = Integer.MAX_VALUE)
  private String artifactRefsJson;

  @Column(name = "provider_diagnostics_json", nullable = false, length = Integer.MAX_VALUE)
  private String providerDiagnosticsJson;

  @Column(name = "identity_mapping_complete", nullable = false)
  private boolean identityMappingComplete;

  @Column(name = "audit_sink_available", nullable = false)
  private boolean auditSinkAvailable;

  @Column(name = "admin_approved", nullable = false)
  private boolean adminApproved;

  @Column(name = "recorded_at_utc", nullable = false)
  private Instant recordedAt;

  @Column(name = "expires_at_utc")
  private Instant expiresAt;

  protected MigrationRunEvidenceJpaEntity() {}

  public MigrationRunEvidenceJpaEntity(
      MigrationRunEvidenceId id,
      String lifecycle,
      String objectCountsJson,
      String contentHashesJson,
      String auditRefsJson,
      String artifactRefsJson,
      String providerDiagnosticsJson,
      boolean identityMappingComplete,
      boolean auditSinkAvailable,
      boolean adminApproved,
      Instant recordedAt,
      Instant expiresAt) {
    this.id = id;
    this.lifecycle = lifecycle;
    this.objectCountsJson = objectCountsJson;
    this.contentHashesJson = contentHashesJson;
    this.auditRefsJson = auditRefsJson;
    this.artifactRefsJson = artifactRefsJson;
    this.providerDiagnosticsJson = providerDiagnosticsJson;
    this.identityMappingComplete = identityMappingComplete;
    this.auditSinkAvailable = auditSinkAvailable;
    this.adminApproved = adminApproved;
    this.recordedAt = recordedAt;
    this.expiresAt = expiresAt;
  }

  public MigrationRunEvidenceId id() {
    return id;
  }

  public String lifecycle() {
    return lifecycle;
  }

  public String objectCountsJson() {
    return objectCountsJson;
  }

  public String contentHashesJson() {
    return contentHashesJson;
  }

  public String auditRefsJson() {
    return auditRefsJson;
  }

  public String artifactRefsJson() {
    return artifactRefsJson;
  }

  public String providerDiagnosticsJson() {
    return providerDiagnosticsJson;
  }

  public boolean identityMappingComplete() {
    return identityMappingComplete;
  }

  public boolean auditSinkAvailable() {
    return auditSinkAvailable;
  }

  public boolean adminApproved() {
    return adminApproved;
  }

  public Instant recordedAt() {
    return recordedAt;
  }

  public Instant expiresAt() {
    return expiresAt;
  }
}
