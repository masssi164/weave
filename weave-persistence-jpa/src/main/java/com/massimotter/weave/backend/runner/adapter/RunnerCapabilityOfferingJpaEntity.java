package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.PublicBundlePublication;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.RunnerOffering;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.RunnerState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "weave_runner_capability_offerings",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weave_runner_capability_offering",
                columnNames = {"organization_ref", "runner_id", "capability_definition_id"}),
        indexes = {
            @Index(
                    name = "ix_weave_runner_capability_available",
                    columnList = "organization_ref,capability_definition_id,active,runner_state,available_slots,observed_at_utc"),
            @Index(
                    name = "ix_weave_runner_capability_runner",
                    columnList = "organization_ref,runner_id,active")
        })
class RunnerCapabilityOfferingJpaEntity {

    @Id
    @Column(name = "offering_id", nullable = false, updatable = false)
    private UUID offeringId;

    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "runner_id", nullable = false, length = 135, updatable = false)
    private String runnerId;

    @Column(name = "capability_definition_id", nullable = false, updatable = false)
    private UUID capabilityDefinitionId;

    @Column(name = "capability_id", nullable = false, length = 128, updatable = false)
    private String capabilityId;

    @Column(name = "capability_version", nullable = false, length = 96, updatable = false)
    private String capabilityVersion;

    @Column(name = "contract_digest", nullable = false, length = 71, updatable = false)
    private String contractDigest;

    @Column(name = "public_bundle_digest", nullable = false, length = 71)
    private String publicBundleDigest;

    @Column(name = "bundle_id", nullable = false, length = 128)
    private String bundleId;

    @Column(name = "bundle_version", nullable = false, length = 96)
    private String bundleVersion;

    @Column(name = "runner_state", nullable = false, length = 16)
    private String runnerState;

    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Column(name = "available_slots", nullable = false)
    private int availableSlots;

    @Column(name = "observed_at_utc", nullable = false)
    private OffsetDateTime observedAt;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerCapabilityOfferingJpaEntity() {}

    static RunnerCapabilityOfferingJpaEntity create(
            RunnerCapabilityDefinitionJpaEntity definition,
            PublicBundlePublication publication) {
        RunnerCapabilityOfferingJpaEntity entity = new RunnerCapabilityOfferingJpaEntity();
        entity.offeringId = UUID.randomUUID();
        entity.organizationRef = publication.organizationRef();
        entity.runnerId = publication.runnerId().value();
        entity.capabilityDefinitionId = definition.definitionId();
        entity.capabilityId = definition.capability().id().value();
        entity.capabilityVersion = definition.capability().version();
        entity.contractDigest = definition.contractDigest();
        entity.apply(publication);
        entity.active = true;
        return entity;
    }

    String coordinate() {
        return capabilityId + "@" + capabilityVersion;
    }

    UUID definitionId() {
        return capabilityDefinitionId;
    }

    boolean update(PublicBundlePublication publication) {
        requirePublicationIdentity(publication);
        Instant current = observedAt.toInstant();
        if (publication.observedAt().isBefore(current)) {
            throw new IllegalStateException("stale Runner capability publication");
        }
        boolean sameValues = publicBundleDigest.equals(publication.publicBundleDigest())
                && bundleId.equals(publication.bundleId())
                && bundleVersion.equals(publication.bundleVersion())
                && runnerState.equals(publication.runnerState().name())
                && capacity == publication.capacity()
                && availableSlots == publication.availableSlots()
                && active;
        if (publication.observedAt().equals(current)) {
            if (sameValues) {
                return false;
            }
            throw new IllegalStateException(
                    "conflicting Runner capability publication at the same observedAt");
        }
        apply(publication);
        active = true;
        return true;
    }

    boolean deactivate(Instant publicationObservedAt) {
        Instant current = observedAt.toInstant();
        if (publicationObservedAt.isBefore(current)) {
            throw new IllegalStateException("stale Runner capability publication");
        }
        if (!active) {
            return false;
        }
        if (publicationObservedAt.equals(current)) {
            throw new IllegalStateException(
                    "conflicting Runner capability publication at the same observedAt");
        }
        active = false;
        observedAt = RunnerPersistenceTime.utc(publicationObservedAt);
        availableSlots = 0;
        return true;
    }

    RunnerOffering snapshot() {
        return new RunnerOffering(
                offeringId,
                organizationRef,
                new RunnerId(runnerId),
                new CapabilityRef(new CapabilityId(capabilityId), capabilityVersion),
                contractDigest,
                publicBundleDigest,
                bundleId,
                bundleVersion,
                RunnerState.valueOf(runnerState),
                capacity,
                availableSlots,
                observedAt.toInstant(),
                active);
    }

    private void apply(PublicBundlePublication publication) {
        publicBundleDigest = publication.publicBundleDigest();
        bundleId = publication.bundleId();
        bundleVersion = publication.bundleVersion();
        runnerState = publication.runnerState().name();
        capacity = publication.capacity();
        availableSlots = publication.availableSlots();
        observedAt = RunnerPersistenceTime.utc(publication.observedAt());
    }

    private void requirePublicationIdentity(PublicBundlePublication publication) {
        if (!organizationRef.equals(publication.organizationRef())
                || !runnerId.equals(publication.runnerId().value())) {
            throw new IllegalArgumentException("Runner offering publication identity mismatch");
        }
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RunnerCapabilityOfferingJpaEntity entity
                        && Objects.equals(offeringId, entity.offeringId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(offeringId);
    }
}
