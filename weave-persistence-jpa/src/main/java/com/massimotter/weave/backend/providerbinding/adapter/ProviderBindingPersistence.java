package com.massimotter.weave.backend.providerbinding.adapter;

import com.massimotter.weave.backend.providerbinding.domain.ProviderBinding;
import com.massimotter.weave.backend.providerbinding.domain.ProviderObjectMapping;
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
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@Entity
@Table(name = "weave_provider_bindings")
class ProviderBindingJpaEntity {

    @EmbeddedId
    private ProviderBindingId id;

    @Column(name = "adapter_key", nullable = false, length = 160, updatable = false)
    private String adapterKey;

    @Column(name = "configuration_ref", nullable = false, length = 255, updatable = false)
    private String configurationRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "binding_state", nullable = false, length = 32)
    private ProviderBinding.State state;

    @Column(name = "active_slot")
    private Boolean activeSlot;

    @Column(name = "activated_at_utc", nullable = false, updatable = false)
    private OffsetDateTime activatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderBindingJpaEntity() {
    }

    static ProviderBindingJpaEntity active(
            String organizationRef,
            String domain,
            long revision,
            String adapterKey,
            String configurationRef,
            Instant activatedAt) {
        ProviderBindingJpaEntity entity = new ProviderBindingJpaEntity();
        entity.id = new ProviderBindingId(organizationRef, domain, revision);
        entity.adapterKey = adapterKey;
        entity.configurationRef = configurationRef;
        entity.state = ProviderBinding.State.ACTIVE;
        entity.activeSlot = true;
        entity.activatedAt = activatedAt
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
        return entity;
    }

    void retire() {
        state = ProviderBinding.State.RETIRED;
        activeSlot = null;
    }

    long revision() {
        return id.bindingRevision();
    }

    ProviderBinding toDomain() {
        return new ProviderBinding(
                id.organizationRef(),
                id.domain(),
                id.bindingRevision(),
                adapterKey,
                configurationRef,
                state,
                activatedAt.toInstant());
    }
}

@Embeddable
class ProviderBindingId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "domain_key", nullable = false, length = 80)
    private String domain;

    @Column(name = "binding_revision", nullable = false)
    private long bindingRevision;

    protected ProviderBindingId() {
    }

    ProviderBindingId(
            String organizationRef,
            String domain,
            long bindingRevision) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.bindingRevision = bindingRevision;
    }

    String organizationRef() {
        return organizationRef;
    }

    String domain() {
        return domain;
    }

    long bindingRevision() {
        return bindingRevision;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof ProviderBindingId other
                && bindingRevision == other.bindingRevision
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(domain, other.domain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, domain, bindingRevision);
    }
}

interface ProviderBindingJpaRepository
        extends JpaRepository<ProviderBindingJpaEntity, ProviderBindingId> {

    Optional<ProviderBindingJpaEntity> findByIdOrganizationRefAndIdDomainAndState(
            String organizationRef,
            String domain,
            ProviderBinding.State state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select binding from ProviderBindingJpaEntity binding
            where binding.id.organizationRef = :organizationRef
              and binding.id.domain = :domain
              and binding.state = :state
            """)
    Optional<ProviderBindingJpaEntity> lockActive(
            @Param("organizationRef") String organizationRef,
            @Param("domain") String domain,
            @Param("state") ProviderBinding.State state);
}

@Entity
@Table(name = "weave_provider_object_mappings")
class ProviderObjectMappingJpaEntity {

    @EmbeddedId
    private ProviderObjectMappingId id;

    @Column(name = "provider_object_ref", nullable = false, length = 1024)
    private String providerObjectRef;

    @Column(name = "provenance", nullable = false, length = 255)
    private String provenance;

    @Column(name = "first_observed_at_utc", nullable = false, updatable = false)
    private OffsetDateTime firstObservedAt;

    @Column(name = "last_observed_at_utc", nullable = false)
    private OffsetDateTime lastObservedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected ProviderObjectMappingJpaEntity() {
    }

    static ProviderObjectMappingJpaEntity create(
            ProviderObjectMappingId id,
            ProviderObjectMapping mapping) {
        ProviderObjectMappingJpaEntity entity =
                new ProviderObjectMappingJpaEntity();
        entity.id = id;
        entity.firstObservedAt =
                mapping.firstObservedAt()
                        .truncatedTo(ChronoUnit.MICROS)
                        .atOffset(ZoneOffset.UTC);
        return entity;
    }

    void observe(ProviderObjectMapping mapping) {
        if (!firstObservedAt.toInstant().equals(mapping.firstObservedAt())) {
            throw new IllegalArgumentException(
                    "provider mapping first observation cannot be rewritten");
        }
        if (lastObservedAt != null
                && mapping.lastObservedAt().isBefore(lastObservedAt.toInstant())) {
            throw new IllegalArgumentException(
                    "provider mapping observation cannot move backwards");
        }
        providerObjectRef = mapping.providerObjectRef();
        provenance = mapping.provenance();
        lastObservedAt = mapping.lastObservedAt()
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
    }

    ProviderObjectMapping toDomain() {
        return new ProviderObjectMapping(
                id.organizationRef(),
                id.domain(),
                id.bindingRevision(),
                id.canonicalObjectId(),
                providerObjectRef,
                provenance,
                firstObservedAt.toInstant(),
                lastObservedAt.toInstant());
    }
}

@Embeddable
class ProviderObjectMappingId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "domain_key", nullable = false, length = 80)
    private String domain;

    @Column(name = "binding_revision", nullable = false)
    private long bindingRevision;

    @Column(name = "canonical_object_id", nullable = false, length = 255)
    private String canonicalObjectId;

    protected ProviderObjectMappingId() {
    }

    ProviderObjectMappingId(
            String organizationRef,
            String domain,
            long bindingRevision,
            String canonicalObjectId) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.domain = Objects.requireNonNull(domain, "domain");
        this.bindingRevision = bindingRevision;
        this.canonicalObjectId = Objects.requireNonNull(canonicalObjectId, "canonicalObjectId");
    }

    static ProviderObjectMappingId from(ProviderObjectMapping mapping) {
        return new ProviderObjectMappingId(
                mapping.organizationRef(),
                mapping.domain(),
                mapping.bindingRevision(),
                mapping.canonicalObjectId());
    }

    String organizationRef() {
        return organizationRef;
    }

    String domain() {
        return domain;
    }

    long bindingRevision() {
        return bindingRevision;
    }

    String canonicalObjectId() {
        return canonicalObjectId;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof ProviderObjectMappingId other
                && bindingRevision == other.bindingRevision
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(domain, other.domain)
                && Objects.equals(canonicalObjectId, other.canonicalObjectId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                organizationRef,
                domain,
                bindingRevision,
                canonicalObjectId);
    }
}

interface ProviderObjectMappingJpaRepository
        extends JpaRepository<ProviderObjectMappingJpaEntity, ProviderObjectMappingId> {

    Optional<ProviderObjectMappingJpaEntity>
            findByIdOrganizationRefAndIdDomainAndIdBindingRevisionAndProviderObjectRef(
                    String organizationRef,
                    String domain,
                    long bindingRevision,
                    String providerObjectRef);
}
