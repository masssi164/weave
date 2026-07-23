package com.massimotter.weave.backend.agentruntime.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_agent_runtime_state_heads")
class RuntimeStateHeadJpaEntity {

    @Id
    @Column(name = "runtime_state_store_ref", nullable = false, length = 1000, updatable = false)
    private String runtimeStateStoreRef;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "current_generation", nullable = false)
    private long currentGeneration;

    @Column(name = "current_generation_ref", length = 81)
    private String currentGenerationRef;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "audit_ref", nullable = false, length = 255)
    private String auditRef;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected RuntimeStateHeadJpaEntity() {
    }

    static RuntimeStateHeadJpaEntity create(
            String storeRef,
            String organizationRef,
            String personRef,
            String cellRef,
            String auditRef,
            Instant now) {
        RuntimeStateHeadJpaEntity entity = new RuntimeStateHeadJpaEntity();
        entity.runtimeStateStoreRef = storeRef;
        entity.organizationRef = organizationRef;
        entity.personRef = personRef;
        entity.cellRef = cellRef;
        entity.auditRef = auditRef;
        entity.createdAt = RuntimePersistenceTime.utc(now);
        entity.updatedAt = RuntimePersistenceTime.utc(now);
        return entity;
    }

    void advance(
            long expectedGeneration,
            long nextGeneration,
            String generationRef,
            String nextAuditRef,
            Instant now) {
        if (currentGeneration != expectedGeneration
                || nextGeneration != currentGeneration + 1) {
            throw new IllegalStateException("stale-runtime-state-head");
        }
        currentGeneration = nextGeneration;
        currentGenerationRef = generationRef;
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    void clear(String nextAuditRef, Instant now) {
        currentGeneration = 0;
        currentGenerationRef = null;
        auditRef = nextAuditRef;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    String runtimeStateStoreRef() {
        return runtimeStateStoreRef;
    }

    String organizationRef() {
        return organizationRef;
    }

    String personRef() {
        return personRef;
    }

    String cellRef() {
        return cellRef;
    }

    long currentGeneration() {
        return currentGeneration;
    }

    String currentGenerationRef() {
        return currentGenerationRef;
    }

    long version() {
        return version;
    }
}

interface RuntimeStateHeadJpaRepository
        extends JpaRepository<RuntimeStateHeadJpaEntity, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select head from RuntimeStateHeadJpaEntity head
            where head.runtimeStateStoreRef = :storeRef
            """)
    Optional<RuntimeStateHeadJpaEntity> lockByStoreRef(
            @Param("storeRef") String storeRef);
}

@Entity
@Table(name = "weave_agent_runtime_state_generations")
class RuntimeStateGenerationJpaEntity {

    @Id
    @Column(name = "generation_ref", nullable = false, length = 81, updatable = false)
    private String generationRef;

    @Column(name = "runtime_state_store_ref", nullable = false, length = 1000, updatable = false)
    private String runtimeStateStoreRef;

    @Column(name = "generation", nullable = false, updatable = false)
    private long generation;

    @Column(name = "previous_generation", nullable = false, updatable = false)
    private long previousGeneration;

    @Column(name = "runtime_profile_hash", nullable = false, length = 71, updatable = false)
    private String runtimeProfileHash;

    @Column(name = "idempotency_key", nullable = false, length = 128, updatable = false)
    private String idempotencyKey;

    @Column(name = "encryption_algorithm", nullable = false, length = 64, updatable = false)
    private String encryptionAlgorithm;

    @Column(name = "wrapping_key_ref", nullable = false, length = 128, updatable = false)
    private String wrappingKeyRef;

    @Column(name = "wrapped_data_key", nullable = false, updatable = false)
    private byte[] wrappedDataKey;

    @Column(name = "nonce", nullable = false, updatable = false)
    private byte[] nonce;

    @Column(name = "plaintext_bytes", nullable = false, updatable = false)
    private long plaintextBytes;

    @Column(name = "ciphertext_bytes", nullable = false, updatable = false)
    private long ciphertextBytes;

    @Column(name = "chunk_count", nullable = false, updatable = false)
    private int chunkCount;

    @Column(name = "audit_ref", nullable = false, length = 255, updatable = false)
    private String auditRef;

    @Column(name = "committed_at", nullable = false, updatable = false)
    private OffsetDateTime committedAt;

    protected RuntimeStateGenerationJpaEntity() {
    }

    static RuntimeStateGenerationJpaEntity create(
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
            int chunkCount,
            String auditRef,
            Instant committedAt) {
        RuntimeStateGenerationJpaEntity entity =
                new RuntimeStateGenerationJpaEntity();
        entity.generationRef = generationRef;
        entity.runtimeStateStoreRef = storeRef;
        entity.generation = generation;
        entity.previousGeneration = previousGeneration;
        entity.runtimeProfileHash = profileHash;
        entity.idempotencyKey = idempotencyKey;
        entity.encryptionAlgorithm = algorithm;
        entity.wrappingKeyRef = wrappingKeyRef;
        entity.wrappedDataKey = wrappedDataKey.clone();
        entity.nonce = nonce.clone();
        entity.plaintextBytes = plaintextBytes;
        entity.ciphertextBytes = ciphertextBytes;
        entity.chunkCount = chunkCount;
        entity.auditRef = auditRef;
        entity.committedAt = RuntimePersistenceTime.utc(committedAt);
        return entity;
    }

    S3EncryptedRuntimeStateStore.StoredGeneration stored() {
        return new S3EncryptedRuntimeStateStore.StoredGeneration(
                generationRef,
                runtimeStateStoreRef,
                generation,
                previousGeneration,
                runtimeProfileHash,
                encryptionAlgorithm,
                wrappingKeyRef,
                wrappedDataKey.clone(),
                nonce.clone(),
                plaintextBytes,
                ciphertextBytes,
                chunkCount,
                committedAt.toInstant());
    }

    String generationRef() {
        return generationRef;
    }
}

interface RuntimeStateGenerationJpaRepository
        extends JpaRepository<RuntimeStateGenerationJpaEntity, String> {

    Optional<RuntimeStateGenerationJpaEntity>
            findByRuntimeStateStoreRefAndIdempotencyKey(
                    String runtimeStateStoreRef,
                    String idempotencyKey);

    List<RuntimeStateGenerationJpaEntity>
            findByRuntimeStateStoreRefOrderByGeneration(
                    String runtimeStateStoreRef);

    long countByRuntimeStateStoreRef(String runtimeStateStoreRef);
}

@Entity
@Table(name = "weave_agent_runtime_state_deletions")
class RuntimeStateDeletionJpaEntity {

    @EmbeddedId
    private RuntimeStateDeletionId id;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "runtime_state_store_ref", nullable = false, length = 1000, updatable = false)
    private String runtimeStateStoreRef;

    @Column(name = "deleted_generation_count", nullable = false, updatable = false)
    private long deletedGenerationCount;

    @Column(name = "audit_ref", nullable = false, length = 255, updatable = false)
    private String auditRef;

    @Column(name = "completed_at", nullable = false, updatable = false)
    private OffsetDateTime completedAt;

    protected RuntimeStateDeletionJpaEntity() {
    }

    static RuntimeStateDeletionJpaEntity create(
            RuntimeStateDeletionId id,
            String cellRef,
            String storeRef,
            long deletedGenerationCount,
            String auditRef,
            Instant completedAt) {
        RuntimeStateDeletionJpaEntity entity =
                new RuntimeStateDeletionJpaEntity();
        entity.id = id;
        entity.cellRef = cellRef;
        entity.runtimeStateStoreRef = storeRef;
        entity.deletedGenerationCount = deletedGenerationCount;
        entity.auditRef = auditRef;
        entity.completedAt = RuntimePersistenceTime.utc(completedAt);
        return entity;
    }

    S3EncryptedRuntimeStateStore.Deletion deletion() {
        return new S3EncryptedRuntimeStateStore.Deletion(
                cellRef,
                runtimeStateStoreRef);
    }
}

@Embeddable
class RuntimeStateDeletionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255)
    private String personRef;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected RuntimeStateDeletionId() {
    }

    RuntimeStateDeletionId(
            String organizationRef,
            String personRef,
            String idempotencyKey) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.personRef = Objects.requireNonNull(personRef, "personRef");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof RuntimeStateDeletionId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(personRef, other.personRef)
                && Objects.equals(idempotencyKey, other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, personRef, idempotencyKey);
    }
}

interface RuntimeStateDeletionJpaRepository
        extends JpaRepository<RuntimeStateDeletionJpaEntity, RuntimeStateDeletionId> {
}
