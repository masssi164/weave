package com.massimotter.weave.backend.files.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Adapter-private, append-only identity of the native Files payload volume. */
@Entity
@Table(name = "weave_files_volume_authorities")
public class FilesVolumeAuthorityJpaEntity {

  @Id
  @Column(name = "authority_key", length = 64, nullable = false, updatable = false)
  private String authorityKey;

  @Column(name = "volume_ref", length = 36, nullable = false, updatable = false)
  private String volumeRef;

  @Column(name = "generation_ref", length = 36, nullable = false, updatable = false)
  private String generationRef;

  @Column(name = "transition_kind", length = 32, nullable = false, updatable = false)
  private String transitionKind;

  @Column(name = "transition_ref", length = 36, nullable = false, updatable = false)
  private String transitionRef;

  @Column(
      name = "transition_receipt_digest",
      length = 71,
      nullable = false,
      updatable = false)
  private String transitionReceiptDigest;

  @Column(
      name = "schema_history_fingerprint",
      length = 64,
      nullable = false,
      updatable = false)
  private String schemaHistoryFingerprint;

  @Column(name = "root_marker_digest", length = 71, nullable = false, updatable = false)
  private String rootMarkerDigest;

  @Column(name = "created_at_utc", nullable = false, updatable = false)
  private Instant createdAt;

  protected FilesVolumeAuthorityJpaEntity() {}

  public FilesVolumeAuthorityJpaEntity(
      String authorityKey,
      String volumeRef,
      String generationRef,
      String transitionKind,
      String transitionRef,
      String transitionReceiptDigest,
      String schemaHistoryFingerprint,
      String rootMarkerDigest,
      Instant createdAt) {
    this.authorityKey = authorityKey;
    this.volumeRef = volumeRef;
    this.generationRef = generationRef;
    this.transitionKind = transitionKind;
    this.transitionRef = transitionRef;
    this.transitionReceiptDigest = transitionReceiptDigest;
    this.schemaHistoryFingerprint = schemaHistoryFingerprint;
    this.rootMarkerDigest = rootMarkerDigest;
    this.createdAt = createdAt;
  }

  public String authorityKey() {
    return authorityKey;
  }

  public String volumeRef() {
    return volumeRef;
  }

  public String generationRef() {
    return generationRef;
  }

  public String transitionKind() {
    return transitionKind;
  }

  public String transitionRef() {
    return transitionRef;
  }

  public String transitionReceiptDigest() {
    return transitionReceiptDigest;
  }

  public String schemaHistoryFingerprint() {
    return schemaHistoryFingerprint;
  }

  public String rootMarkerDigest() {
    return rootMarkerDigest;
  }

  public Instant createdAt() {
    return createdAt;
  }
}
