package com.massimotter.weave.backend.identity;

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
import org.springframework.data.jpa.repository.JpaRepository;

/** Adapter-private identity binding defined by the canonical Chen model. */
@Entity
@Table(name = "weave_person_bindings")
class PersonBindingJpaEntity {
    @EmbeddedId
    private PersonBindingId id;

    @Column(name = "person_ref", nullable = false, length = 255, updatable = false)
    private String personRef;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected PersonBindingJpaEntity() {
    }

    static PersonBindingJpaEntity create(
            String organizationRef,
            String issuer,
            String subject,
            String personRef,
            Instant createdAt) {
        PersonBindingJpaEntity entity = new PersonBindingJpaEntity();
        entity.id = new PersonBindingId(organizationRef, issuer, subject);
        entity.personRef = Objects.requireNonNull(personRef, "personRef");
        entity.createdAt = Objects.requireNonNull(createdAt, "createdAt")
                .truncatedTo(ChronoUnit.MICROS)
                .atOffset(ZoneOffset.UTC);
        return entity;
    }

    String personRef() {
        return personRef;
    }
}

@Embeddable
class PersonBindingId implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "organization_ref", nullable = false, length = 255)
    private String organizationRef;

    @Column(name = "issuer", nullable = false, length = 500)
    private String issuer;

    @Column(name = "subject", nullable = false, length = 255)
    private String subject;

    protected PersonBindingId() {
    }

    PersonBindingId(String organizationRef, String issuer, String subject) {
        this.organizationRef = Objects.requireNonNull(organizationRef, "organizationRef");
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.subject = Objects.requireNonNull(subject, "subject");
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof PersonBindingId other
                && Objects.equals(organizationRef, other.organizationRef)
                && Objects.equals(issuer, other.issuer)
                && Objects.equals(subject, other.subject);
    }

    @Override
    public int hashCode() {
        return Objects.hash(organizationRef, issuer, subject);
    }
}

interface PersonBindingJpaRepository
        extends JpaRepository<PersonBindingJpaEntity, PersonBindingId> {
    Optional<PersonBindingJpaEntity> findByIdOrganizationRefAndPersonRef(
            String organizationRef,
            String personRef);
}
