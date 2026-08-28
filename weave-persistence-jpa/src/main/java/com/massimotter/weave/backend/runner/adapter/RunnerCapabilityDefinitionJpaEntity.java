package com.massimotter.weave.backend.runner.adapter;

import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityContract;
import com.massimotter.weave.backend.runner.application.RunnerCapabilityRegistry.CapabilityDefinition;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityDescriptor;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityEffect;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityId;
import com.massimotter.weave.backend.runner.domain.RunnerControl.CapabilityRef;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(
        name = "weave_runner_capability_definitions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_weave_runner_capability_coordinate",
                columnNames = {"organization_ref", "capability_id", "capability_version"}),
        indexes = @Index(
                name = "ix_weave_runner_capability_catalog",
                columnList = "organization_ref,introduced_revision,capability_id,capability_version"))
class RunnerCapabilityDefinitionJpaEntity {

    @Id
    @Column(name = "capability_definition_id", nullable = false, updatable = false)
    private UUID definitionId;

    @Column(name = "organization_ref", nullable = false, length = 256, updatable = false)
    private String organizationRef;

    @Column(name = "capability_id", nullable = false, length = 128, updatable = false)
    private String capabilityId;

    @Column(name = "capability_version", nullable = false, length = 96, updatable = false)
    private String capabilityVersion;

    @Column(name = "contract_digest", nullable = false, length = 71, updatable = false)
    private String contractDigest;

    @Column(name = "title", nullable = false, length = 160, updatable = false)
    private String title;

    @Column(name = "description", nullable = false, length = 1000, updatable = false)
    private String description;

    @Column(name = "capability_effect", nullable = false, length = 32, updatable = false)
    private String effect;

    @Column(name = "input_schema_json", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String inputSchemaJson;

    @Column(name = "input_schema_digest", nullable = false, length = 71, updatable = false)
    private String inputSchemaDigest;

    @Column(name = "output_schema_json", nullable = false, length = Integer.MAX_VALUE, updatable = false)
    private String outputSchemaJson;

    @Column(name = "output_schema_digest", nullable = false, length = 71, updatable = false)
    private String outputSchemaDigest;

    @Column(name = "timeout_seconds", nullable = false, updatable = false)
    private int timeoutSeconds;

    @Column(name = "maximum_output_bytes", nullable = false, updatable = false)
    private long maximumOutputBytes;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "weave_runner_capability_artifact_types",
            joinColumns = @JoinColumn(name = "capability_definition_id"))
    @Column(name = "artifact_type", nullable = false, length = 160)
    private Set<String> artifactTypes = new HashSet<>();

    @Column(name = "introduced_revision", nullable = false, updatable = false)
    private long introducedRevision;

    @Column(name = "created_at_utc", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected RunnerCapabilityDefinitionJpaEntity() {}

    static RunnerCapabilityDefinitionJpaEntity create(
            String organizationRef,
            CapabilityContract contract,
            long introducedRevision,
            Instant createdAt) {
        CapabilityDescriptor descriptor = contract.descriptor();
        RunnerCapabilityDefinitionJpaEntity entity = new RunnerCapabilityDefinitionJpaEntity();
        entity.definitionId = UUID.randomUUID();
        entity.organizationRef = organizationRef;
        entity.capabilityId = descriptor.capability().id().value();
        entity.capabilityVersion = descriptor.capability().version();
        entity.contractDigest = contract.contractDigest();
        entity.title = descriptor.title();
        entity.description = descriptor.description();
        entity.effect = descriptor.effect().name();
        entity.inputSchemaJson = descriptor.inputSchemaJson();
        entity.inputSchemaDigest = descriptor.inputSchemaDigest();
        entity.outputSchemaJson = descriptor.outputSchemaJson();
        entity.outputSchemaDigest = descriptor.outputSchemaDigest();
        entity.timeoutSeconds = Math.toIntExact(descriptor.timeout().toSeconds());
        entity.maximumOutputBytes = descriptor.maximumOutputBytes();
        entity.artifactTypes.addAll(descriptor.artifactTypes());
        entity.introducedRevision = introducedRevision;
        entity.createdAt = RunnerPersistenceTime.utc(createdAt);
        return entity;
    }

    UUID definitionId() {
        return definitionId;
    }

    CapabilityRef capability() {
        return new CapabilityRef(new CapabilityId(capabilityId), capabilityVersion);
    }

    String contractDigest() {
        return contractDigest;
    }

    boolean matches(CapabilityContract contract) {
        CapabilityDescriptor descriptor = contract.descriptor();
        return capabilityId.equals(descriptor.capability().id().value())
                && capabilityVersion.equals(descriptor.capability().version())
                && contractDigest.equals(contract.contractDigest())
                && title.equals(descriptor.title())
                && description.equals(descriptor.description())
                && effect.equals(descriptor.effect().name())
                && inputSchemaJson.equals(descriptor.inputSchemaJson())
                && inputSchemaDigest.equals(descriptor.inputSchemaDigest())
                && outputSchemaJson.equals(descriptor.outputSchemaJson())
                && outputSchemaDigest.equals(descriptor.outputSchemaDigest())
                && timeoutSeconds == descriptor.timeout().toSeconds()
                && maximumOutputBytes == descriptor.maximumOutputBytes()
                && artifactTypes.equals(descriptor.artifactTypes());
    }

    CapabilityDefinition snapshot() {
        CapabilityDescriptor descriptor = new CapabilityDescriptor(
                capability(),
                title,
                description,
                CapabilityEffect.valueOf(effect),
                inputSchemaJson,
                inputSchemaDigest,
                outputSchemaJson,
                outputSchemaDigest,
                Duration.ofSeconds(timeoutSeconds),
                maximumOutputBytes,
                Set.copyOf(artifactTypes));
        return new CapabilityDefinition(
                definitionId,
                organizationRef,
                new CapabilityContract(descriptor, contractDigest),
                introducedRevision,
                createdAt.toInstant());
    }

    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof RunnerCapabilityDefinitionJpaEntity entity
                        && Objects.equals(definitionId, entity.definitionId);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(definitionId);
    }
}
