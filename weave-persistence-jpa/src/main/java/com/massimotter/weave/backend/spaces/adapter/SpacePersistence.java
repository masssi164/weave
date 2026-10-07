package com.massimotter.weave.backend.spaces.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Canonical Space aggregate root. */
@Entity
@Table(name = "weave_spaces")
class SpaceJpaEntity {
    @EmbeddedId
    private SpaceId id;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SpaceJpaEntity() {
    }

    static SpaceJpaEntity active(
            String organizationRef,
            String spaceRef,
            Instant now) {
        SpaceJpaEntity entity = new SpaceJpaEntity();
        entity.id = new SpaceId(organizationRef, spaceRef);
        entity.lifecycleState = "ACTIVE";
        entity.createdAt = utc(now);
        entity.updatedAt = utc(now);
        return entity;
    }

    void transition(String targetState, Instant now) {
        if (!java.util.Set.of("ACTIVE", "SUSPENDED", "DELETED").contains(targetState)) {
            throw new IllegalArgumentException("Space lifecycle state is invalid");
        }
        lifecycleState = targetState;
        updatedAt = utc(now);
    }

    boolean active() {
        return "ACTIVE".equals(lifecycleState);
    }

    static OffsetDateTime utc(Instant value) {
        return Objects.requireNonNull(value, "now")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }
}

@Embeddable
class SpaceId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    protected SpaceId() {
    }

    SpaceId(String organizationRef, String spaceRef) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof SpaceId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, spaceRef);
    }
}

interface SpaceJpaRepository extends JpaRepository<SpaceJpaEntity, SpaceId> {
}

/** Membership is independently versioned to avoid replacing the Space aggregate graph. */
@Entity
@Table(name = "weave_space_memberships")
class SpaceMembershipJpaEntity {
    @EmbeddedId
    private SpaceMembershipId id;

    @Column(name = "permission_set", nullable = false, length = 255)
    private String permissionSet;

    @Column(name = "lifecycle_state", nullable = false, length = 32)
    private String lifecycleState;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected SpaceMembershipJpaEntity() {
    }

    static SpaceMembershipJpaEntity active(
            String organizationRef,
            String spaceRef,
            String personRef,
            String permissionSet,
            Instant now) {
        SpaceMembershipJpaEntity entity = new SpaceMembershipJpaEntity();
        entity.id = new SpaceMembershipId(
                organizationRef, spaceRef, personRef);
        entity.permissionSet =
                Objects.requireNonNull(permissionSet, "permissionSet");
        entity.lifecycleState = "ACTIVE";
        entity.createdAt = SpaceJpaEntity.utc(now);
        entity.updatedAt = entity.createdAt;
        return entity;
    }

    void replacePermissions(String permissionSet, Instant now) {
        this.permissionSet =
                Objects.requireNonNull(permissionSet, "permissionSet");
        updatedAt = Objects.requireNonNull(now, "now")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }

    void revoke(Instant now) {
        lifecycleState = "REVOKED";
        updatedAt = SpaceJpaEntity.utc(now);
    }

    boolean active() {
        return "ACTIVE".equals(lifecycleState);
    }

    String permissionSet() {
        return permissionSet;
    }

    String accountRef() {
        return id.personRef();
    }
}

@Embeddable
class SpaceMembershipId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "space_ref", nullable = false, length = 255)
    private String spaceRef;

    @Column(name = "person_ref", nullable = false, length = 255)
    private String personRef;

    protected SpaceMembershipId() {
    }

    SpaceMembershipId(
            String organizationRef,
            String spaceRef,
            String personRef) {
        this.organizationRef = Objects.requireNonNull(
                organizationRef, "organizationRef");
        this.spaceRef = Objects.requireNonNull(spaceRef, "spaceRef");
        this.personRef = Objects.requireNonNull(personRef, "personRef");
    }

    String personRef() {
        return personRef;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof SpaceMembershipId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(spaceRef, other.spaceRef)
                && Objects.equals(personRef, other.personRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, spaceRef, personRef);
    }
}

interface SpaceMembershipJpaRepository
        extends JpaRepository<SpaceMembershipJpaEntity, SpaceMembershipId> {
    List<SpaceMembershipJpaEntity> findByIdOrganizationRefAndIdSpaceRef(
            String organizationRef, String spaceRef);
    Optional<SpaceMembershipJpaEntity>
            findByIdOrganizationRefAndIdSpaceRefAndIdPersonRef(
                    String organizationRef,
                    String spaceRef,
                    String personRef);

    @Query("""
            select s.id.spaceRef from SpaceJpaEntity s, SpaceMembershipJpaEntity m
            where s.id.organizationRef = :organizationRef
              and m.id.organizationRef = :organizationRef
              and s.id.spaceRef = m.id.spaceRef
              and m.id.personRef = :personRef
              and s.lifecycleState = 'ACTIVE'
              and m.lifecycleState = 'ACTIVE'
              and m.permissionSet in ('VIEW', 'VIEW,EDIT', 'VIEW,EDIT,ADMIN')
              and s.id.spaceRef > :afterSpaceRef
            order by s.id.spaceRef
            """)
    List<String> visibleSpaceRefs(
            @Param("organizationRef") String organizationRef,
            @Param("personRef") String personRef,
            @Param("afterSpaceRef") String afterSpaceRef,
            Pageable page);
}
