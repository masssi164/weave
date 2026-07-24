package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCommandReceipt;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.LockModeType;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_agent_runtime_commands")
class RuntimeCommandJpaEntity {

    @EmbeddedId
    private RuntimeCommandId id;

    @Column(name = "command", nullable = false, length = 64, updatable = false)
    private String command;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private RuntimeCommandReceipt.Status status;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "runtime_version")
    private Long runtimeVersion;

    @Column(name = "audit_ref", nullable = false, length = 255, updatable = false)
    private String auditRef;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RuntimeCommandJpaEntity() {
    }

    static RuntimeCommandJpaEntity started(
            RuntimeCommandId id,
            String command,
            String cellRef,
            String auditRef,
            Instant now) {
        RuntimeCommandJpaEntity entity = new RuntimeCommandJpaEntity();
        entity.id = id;
        entity.command = command;
        entity.status = RuntimeCommandReceipt.Status.STARTED;
        entity.cellRef = cellRef;
        entity.auditRef = auditRef;
        entity.createdAt = RuntimePersistenceTime.utc(now);
        entity.updatedAt = RuntimePersistenceTime.utc(now);
        return entity;
    }

    boolean matches(String requestedCommand, String requestedCellRef) {
        return Objects.equals(command, requestedCommand)
                && Objects.equals(cellRef, requestedCellRef);
    }

    boolean complete(String requestedCommand, long nextRuntimeVersion, Instant now) {
        if (!Objects.equals(command, requestedCommand)) {
            return false;
        }
        if (status == RuntimeCommandReceipt.Status.COMPLETED) {
            return Objects.equals(runtimeVersion, nextRuntimeVersion);
        }
        status = RuntimeCommandReceipt.Status.COMPLETED;
        runtimeVersion = nextRuntimeVersion;
        failureCode = null;
        updatedAt = RuntimePersistenceTime.utc(now);
        return true;
    }

    void fail(String nextFailureCode, Instant now) {
        if (status == RuntimeCommandReceipt.Status.COMPLETED) {
            return;
        }
        status = RuntimeCommandReceipt.Status.FAILED;
        failureCode = nextFailureCode;
        updatedAt = RuntimePersistenceTime.utc(now);
    }

    RuntimeCommandReceipt toDomain() {
        return new RuntimeCommandReceipt(
                id.organizationRef(),
                id.personRef(),
                id.idempotencyKey(),
                command,
                status,
                cellRef,
                runtimeVersion,
                auditRef,
                failureCode,
                createdAt.toInstant(),
                updatedAt.toInstant());
    }
}

@Embeddable
class RuntimeCommandId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255)
    private String personRef;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected RuntimeCommandId() {
    }

    RuntimeCommandId(
            String organizationRef,
            String personRef,
            String idempotencyKey) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.personRef = Objects.requireNonNull(personRef, "personRef");
        this.idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
    }

    String organizationRef() {
        return organizationRef;
    }

    String personRef() {
        return personRef;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof RuntimeCommandId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(personRef, other.personRef)
                && Objects.equals(idempotencyKey, other.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, personRef, idempotencyKey);
    }
}

interface RuntimeCommandJpaRepository
        extends JpaRepository<RuntimeCommandJpaEntity, RuntimeCommandId> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select command from RuntimeCommandJpaEntity command where command.id = :id")
    Optional<RuntimeCommandJpaEntity> lockById(@Param("id") RuntimeCommandId id);
}
