package com.massimotter.weave.backend.persistence.jpa.agentruntime;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "weave_agent_runtime_state_generations")
public class RuntimeStateGenerationEntity {
  @Id
  @Column(name = "generation_ref", length = 81, nullable = false, updatable = false)
  private String generationRef;

  @Column(name = "runtime_state_store_ref", length = 1000, nullable = false, updatable = false)
  private String runtimeStateStoreRef;

  @Column(nullable = false, updatable = false)
  private long generation;

  @Column(name = "previous_generation", nullable = false, updatable = false)
  private long previousGeneration;

  @Column(name = "runtime_profile_hash", length = 71, nullable = false, updatable = false)
  private String runtimeProfileHash;

  @Column(name = "idempotency_key", nullable = false, updatable = false)
  private String idempotencyKey;

  @Column(name = "encryption_algorithm", nullable = false, updatable = false)
  private String encryptionAlgorithm;

  @Column(name = "wrapping_key_ref", nullable = false, updatable = false)
  private String wrappingKeyRef;

  @Column(name = "wrapped_data_key", nullable = false, updatable = false)
  private byte[] wrappedDataKey;

  @Column(nullable = false, updatable = false)
  private byte[] nonce;

  @Column(name = "plaintext_bytes", nullable = false, updatable = false)
  private long plaintextBytes;

  @Column(name = "ciphertext_bytes", nullable = false, updatable = false)
  private long ciphertextBytes;

  @Column(name = "chunk_count", nullable = false, updatable = false)
  private int chunkCount;

  @Column(name = "audit_ref", nullable = false, updatable = false)
  private String auditRef;

  @Column(name = "committed_at", nullable = false, updatable = false)
  private Instant committedAt;

  @ElementCollection(fetch = FetchType.EAGER)
  @CollectionTable(
      name = "weave_agent_runtime_state_chunks",
      joinColumns = @JoinColumn(name = "generation_ref", nullable = false))
  @OrderColumn(name = "chunk_ordinal")
  @Column(name = "ciphertext", nullable = false)
  private List<byte[]> chunks = new ArrayList<>();

  protected RuntimeStateGenerationEntity() {}

  public RuntimeStateGenerationEntity(
      String generationRef,
      String storeRef,
      long generation,
      long previousGeneration,
      String profileHash,
      String idempotencyKey,
      String algorithm,
      String wrappingKeyRef,
      byte[] wrappedDataKey,
      byte[] nonce,
      long plaintextBytes,
      long ciphertextBytes,
      String auditRef,
      Instant committedAt,
      List<byte[]> chunks) {
    this.generationRef = generationRef;
    this.runtimeStateStoreRef = storeRef;
    this.generation = generation;
    this.previousGeneration = previousGeneration;
    this.runtimeProfileHash = profileHash;
    this.idempotencyKey = idempotencyKey;
    this.encryptionAlgorithm = algorithm;
    this.wrappingKeyRef = wrappingKeyRef;
    this.wrappedDataKey = wrappedDataKey.clone();
    this.nonce = nonce.clone();
    this.plaintextBytes = plaintextBytes;
    this.ciphertextBytes = ciphertextBytes;
    this.chunkCount = chunks.size();
    this.auditRef = auditRef;
    this.committedAt = committedAt;
    this.chunks = new ArrayList<>(chunks.stream().map(byte[]::clone).toList());
  }

  public String generationRef() {
    return generationRef;
  }

  public String runtimeStateStoreRef() {
    return runtimeStateStoreRef;
  }

  public long generation() {
    return generation;
  }

  public long previousGeneration() {
    return previousGeneration;
  }

  public String runtimeProfileHash() {
    return runtimeProfileHash;
  }

  public String encryptionAlgorithm() {
    return encryptionAlgorithm;
  }

  public String wrappingKeyRef() {
    return wrappingKeyRef;
  }

  public byte[] wrappedDataKey() {
    return wrappedDataKey.clone();
  }

  public byte[] nonce() {
    return nonce.clone();
  }

  public long plaintextBytes() {
    return plaintextBytes;
  }

  public long ciphertextBytes() {
    return ciphertextBytes;
  }

  public int chunkCount() {
    return chunkCount;
  }

  public Instant committedAt() {
    return committedAt;
  }

  public List<byte[]> chunks() {
    return chunks.stream().map(byte[]::clone).toList();
  }
}
