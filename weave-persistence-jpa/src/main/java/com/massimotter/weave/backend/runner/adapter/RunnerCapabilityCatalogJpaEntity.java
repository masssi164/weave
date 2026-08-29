package com.massimotter.weave.backend.runner.adapter;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;

@Entity
@Table(name = "weave_runner_capability_catalogs")
class RunnerCapabilityCatalogJpaEntity {

    @Id
    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "catalog_revision", nullable = false)
    private long catalogRevision;

    @Column(name = "updated_at_utc", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerCapabilityCatalogJpaEntity() {}

    static RunnerCapabilityCatalogJpaEntity create(String organizationRef, Instant instant) {
        RunnerCapabilityCatalogJpaEntity entity = new RunnerCapabilityCatalogJpaEntity();
        entity.organizationRef = organizationRef;
        entity.catalogRevision = 0;
        entity.updatedAt = RunnerPersistenceTime.utc(instant);
        return entity;
    }

    long revision() {
        return catalogRevision;
    }

    long increment(Instant instant) {
        catalogRevision++;
        updatedAt = RunnerPersistenceTime.utc(instant);
        return catalogRevision;
    }
}
