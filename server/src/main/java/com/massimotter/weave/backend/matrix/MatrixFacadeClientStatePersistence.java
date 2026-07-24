package com.massimotter.weave.backend.matrix;

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
import java.time.ZoneOffset;
import java.util.Objects;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_matrix_identity_projection")
class MatrixIdentityProjectionJpaEntity {

    @EmbeddedId
    private MatrixIdentityProjectionId id;

    @Column(name = "actor_ref", nullable = false, length = 255, updatable = false)
    private String actorRef;

    @Column(name = "authorization_principal_ref", nullable = false, length = 255)
    private String authorizationPrincipalRef;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MatrixIdentityProjectionJpaEntity() {
    }

    static MatrixIdentityProjectionJpaEntity create(
            MatrixIdentityProjectionId id,
            MatrixFacadeClientStateStore.IdentityProjection projection) {
        MatrixIdentityProjectionJpaEntity entity =
                new MatrixIdentityProjectionJpaEntity();
        entity.id = id;
        entity.actorRef = projection.actorRef();
        return entity;
    }

    void apply(MatrixFacadeClientStateStore.IdentityProjection projection) {
        if (!Objects.equals(actorRef, projection.actorRef())) {
            throw new IllegalArgumentException(
                    "Matrix user is already bound to another canonical actor.");
        }
        if (updatedAt != null && projection.updatedAt().isBefore(updatedAt.toInstant())) {
            throw new IllegalArgumentException(
                    "Matrix identity projection update cannot move backwards.");
        }
        authorizationPrincipalRef = projection.authorizationPrincipalRef();
        updatedAt = MatrixPersistenceTime.utc(projection.updatedAt());
    }

    MatrixFacadeClientStateStore.IdentityProjection toProjection() {
        return new MatrixFacadeClientStateStore.IdentityProjection(
                id.tenantId(),
                id.identityIssuer(),
                id.matrixUserId(),
                actorRef,
                authorizationPrincipalRef,
                updatedAt.toInstant());
    }
}

@Embeddable
class MatrixIdentityProjectionId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "tenant_id", nullable = false, length = 160)
    private String tenantId;

    @Column(name = "identity_issuer", nullable = false, length = 512)
    private String identityIssuer;

    @Column(name = "matrix_user_id", nullable = false, length = 255)
    private String matrixUserId;

    protected MatrixIdentityProjectionId() {
    }

    MatrixIdentityProjectionId(
            String tenantId,
            String identityIssuer,
            String matrixUserId) {
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.identityIssuer = Objects.requireNonNull(identityIssuer, "identityIssuer");
        this.matrixUserId = Objects.requireNonNull(matrixUserId, "matrixUserId");
    }

    String tenantId() {
        return tenantId;
    }

    String identityIssuer() {
        return identityIssuer;
    }

    String matrixUserId() {
        return matrixUserId;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof MatrixIdentityProjectionId other
                && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(identityIssuer, other.identityIssuer)
                && Objects.equals(matrixUserId, other.matrixUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tenantId, identityIssuer, matrixUserId);
    }
}

@Entity
@Table(name = "weave_matrix_revoked_sessions")
class MatrixRevokedSessionJpaEntity {

    @Id
    @Column(name = "session_hash", nullable = false, length = 64, updatable = false)
    private String sessionHash;

    @Column(name = "revoked_at_utc", nullable = false, updatable = false)
    private OffsetDateTime revokedAt;

    @Column(name = "expires_at_utc", nullable = false, updatable = false)
    private OffsetDateTime expiresAt;

    protected MatrixRevokedSessionJpaEntity() {
    }

    static MatrixRevokedSessionJpaEntity create(
            String sessionHash,
            Instant revokedAt,
            Instant expiresAt) {
        MatrixRevokedSessionJpaEntity entity = new MatrixRevokedSessionJpaEntity();
        entity.sessionHash = sessionHash;
        entity.revokedAt = MatrixPersistenceTime.utc(revokedAt);
        entity.expiresAt = MatrixPersistenceTime.utc(expiresAt);
        return entity;
    }

    void requireEquivalent(Instant requestedRevokedAt, Instant requestedExpiresAt) {
        if (!revokedAt.toInstant().equals(requestedRevokedAt)
                || !expiresAt.toInstant().equals(requestedExpiresAt)) {
            throw new IllegalArgumentException(
                    "Matrix revocation digest is already bound to another session window.");
        }
    }
}

interface MatrixIdentityProjectionJpaRepository
        extends JpaRepository<MatrixIdentityProjectionJpaEntity, MatrixIdentityProjectionId> {
}

interface MatrixRevokedSessionJpaRepository
        extends JpaRepository<MatrixRevokedSessionJpaEntity, String> {

    long deleteByExpiresAtLessThanEqual(OffsetDateTime expiresAt);

    boolean existsBySessionHashAndExpiresAtAfter(
            String sessionHash,
            OffsetDateTime expiresAt);
}

final class MatrixPersistenceTime {

    private MatrixPersistenceTime() {
    }

    static OffsetDateTime utc(Instant value) {
        return Objects.requireNonNull(value, "timestamp").atOffset(ZoneOffset.UTC);
    }
}
