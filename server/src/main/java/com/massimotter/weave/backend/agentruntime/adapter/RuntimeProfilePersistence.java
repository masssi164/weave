package com.massimotter.weave.backend.agentruntime.adapter;

import com.massimotter.weave.backend.agentruntime.domain.RuntimeCell;
import com.massimotter.weave.backend.agentruntime.domain.RuntimeEntitlementState;
import com.massimotter.weave.backend.agentruntime.domain.SignedRuntimeProfile;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_agent_runtime_profiles")
class RuntimeProfileJpaEntity {

    @Id
    @Column(name = "profile_hash", nullable = false, length = 71, updatable = false)
    private String profileHash;

    @Column(name = "profile_id", nullable = false, length = 255, updatable = false)
    private String profileId;

    @Column(name = "cell_ref", nullable = false, length = 255, updatable = false)
    private String cellRef;

    @Column(name = "organization_ref", nullable = false, length = 255, updatable = false)
    private String organizationRef;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "payload", nullable = false, updatable = false)
    private String payload;

    @Column(name = "selected_key_id", nullable = false, length = 255)
    private String selectedKeyId;

    @Column(name = "issued_at", nullable = false, updatable = false)
    private OffsetDateTime issuedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "revocation_code", length = 100)
    private String revocationCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RuntimeProfileJpaEntity() {
    }

    static RuntimeProfileJpaEntity create(
            RuntimeCell cell,
            SignedRuntimeProfile profile,
            Instant now) {
        RuntimeProfileJpaEntity entity = new RuntimeProfileJpaEntity();
        entity.profileHash = profile.profileHash();
        entity.profileId = profile.profileId();
        entity.cellRef = profile.cellRef();
        entity.organizationRef = cell.organizationRef();
        entity.personRef = cell.personRef();
        entity.payload = profile.payload();
        entity.selectedKeyId = profile.keyId();
        entity.issuedAt = RuntimePersistenceTime.utc(profile.issuedAt());
        entity.expiresAt = RuntimePersistenceTime.utc(profile.expiresAt());
        entity.createdAt = RuntimePersistenceTime.utc(now);
        return entity;
    }

    void requireSamePayload(RuntimeCell cell, SignedRuntimeProfile profile) {
        if (!Objects.equals(profileHash, profile.profileHash())
                || !Objects.equals(profileId, profile.profileId())
                || !Objects.equals(cellRef, profile.cellRef())
                || !Objects.equals(organizationRef, cell.organizationRef())
                || !Objects.equals(personRef, cell.personRef())
                || !Objects.equals(payload, profile.payload())
                || !issuedAt.toInstant().equals(
                        RuntimePersistenceTime.utc(profile.issuedAt()).toInstant())
                || !expiresAt.toInstant().equals(
                        RuntimePersistenceTime.utc(profile.expiresAt()).toInstant())) {
            throw new IllegalStateException(
                    "RuntimeProfile hash or id is already bound to other semantics");
        }
        if (revokedAt != null) {
            throw new IllegalStateException(
                    "revoked RuntimeProfile cannot select a signing key");
        }
    }

    void select(String keyId) {
        if (revokedAt != null) {
            throw new IllegalStateException(
                    "revoked RuntimeProfile cannot select a signing key");
        }
        selectedKeyId = keyId;
    }

    void revoke(String code, Instant now) {
        if (revokedAt == null) {
            revokedAt = RuntimePersistenceTime.utc(now);
            revocationCode = code;
        }
    }

    String profileHash() {
        return profileHash;
    }

    String selectedKeyId() {
        return selectedKeyId;
    }

    SignedRuntimeProfile toDomain(RuntimeProfileSignatureJpaEntity signature) {
        return new SignedRuntimeProfile(
                signature.protectedHeader(),
                payload,
                signature.signature(),
                profileHash,
                profileId,
                cellRef,
                signature.keyId(),
                issuedAt.toInstant(),
                expiresAt.toInstant());
    }
}

interface RuntimeProfileJpaRepository
        extends JpaRepository<RuntimeProfileJpaEntity, String> {

    Optional<RuntimeProfileJpaEntity> findByCellRefAndProfileHash(
            String cellRef,
            String profileHash);

    @Query("""
            select profile
            from RuntimeProfileJpaEntity profile, RuntimeCellJpaEntity cell
            where profile.profileHash = :profileHash
              and cell.cellRef = profile.cellRef
              and cell.runtimeProfileHash = profile.profileHash
              and cell.runtimeProfileId = profile.profileId
              and cell.entitlementState = :entitled
              and profile.revokedAt is null
              and profile.issuedAt <= :now
              and profile.expiresAt > :now
              and cell.workloadIssuer = :workloadIssuer
              and cell.workloadSubject = :workloadSubject
              and cell.workloadClientId = :workloadClientId
            """)
    Optional<RuntimeProfileJpaEntity> findCurrentForWorkload(
            @Param("profileHash") String profileHash,
            @Param("workloadIssuer") String workloadIssuer,
            @Param("workloadSubject") String workloadSubject,
            @Param("workloadClientId") String workloadClientId,
            @Param("now") OffsetDateTime now,
            @Param("entitled") RuntimeEntitlementState entitled);
}

@Entity
@Table(name = "weave_agent_runtime_profile_signatures")
class RuntimeProfileSignatureJpaEntity {

    @EmbeddedId
    private RuntimeProfileSignatureId id;

    @Column(name = "protected_header", nullable = false, updatable = false)
    private String protectedHeader;

    @Column(name = "signature", nullable = false, updatable = false)
    private String signature;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected RuntimeProfileSignatureJpaEntity() {
    }

    static RuntimeProfileSignatureJpaEntity create(
            RuntimeProfileSignatureId id,
            SignedRuntimeProfile profile,
            Instant now) {
        RuntimeProfileSignatureJpaEntity entity =
                new RuntimeProfileSignatureJpaEntity();
        entity.id = id;
        entity.protectedHeader = profile.protectedHeader();
        entity.signature = profile.signature();
        entity.createdAt = RuntimePersistenceTime.utc(now);
        return entity;
    }

    void requireEquivalent(SignedRuntimeProfile profile) {
        if (!Objects.equals(protectedHeader, profile.protectedHeader())
                || !Objects.equals(signature, profile.signature())) {
            throw new IllegalStateException(
                    "RuntimeProfile key id is already bound to another signature");
        }
    }

    String keyId() {
        return id.keyId();
    }

    String protectedHeader() {
        return protectedHeader;
    }

    String signature() {
        return signature;
    }
}

@Embeddable
class RuntimeProfileSignatureId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "profile_hash", nullable = false, length = 71)
    private String profileHash;

    @Column(name = "key_id", nullable = false, length = 255)
    private String keyId;

    protected RuntimeProfileSignatureId() {
    }

    RuntimeProfileSignatureId(String profileHash, String keyId) {
        this.profileHash = Objects.requireNonNull(profileHash, "profileHash");
        this.keyId = Objects.requireNonNull(keyId, "keyId");
    }

    String keyId() {
        return keyId;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof RuntimeProfileSignatureId other
                && Objects.equals(profileHash, other.profileHash)
                && Objects.equals(keyId, other.keyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileHash, keyId);
    }
}

interface RuntimeProfileSignatureJpaRepository
        extends JpaRepository<RuntimeProfileSignatureJpaEntity, RuntimeProfileSignatureId> {
}
