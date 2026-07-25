package com.massimotter.weave.backend.service.migration;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "weave_migration_run_evidence")
class MigrationRunEvidenceJpaEntity {

    @EmbeddedId
    private MigrationRunEvidenceId id;

    @Column(name = "lifecycle", nullable = false, length = 80)
    private String lifecycle;

    @Column(name = "object_counts_json", nullable = false)
    private String objectCountsJson;

    @Column(name = "content_hashes_json", nullable = false)
    private String contentHashesJson;

    @Column(name = "audit_refs_json", nullable = false)
    private String auditRefsJson;

    @Column(name = "artifact_refs_json", nullable = false)
    private String artifactRefsJson;

    @Column(name = "provider_diagnostics_json", nullable = false)
    private String providerDiagnosticsJson;

    @Column(name = "identity_mapping_complete", nullable = false)
    private boolean identityMappingComplete;

    @Column(name = "audit_sink_available", nullable = false)
    private boolean auditSinkAvailable;

    @Column(name = "admin_approved", nullable = false)
    private boolean adminApproved;

    @Column(name = "recorded_at_utc", nullable = false)
    private OffsetDateTime recordedAt;

    @Column(name = "expires_at_utc")
    private OffsetDateTime expiresAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected MigrationRunEvidenceJpaEntity() {
    }

    static MigrationRunEvidenceJpaEntity create(MigrationRunEvidenceId id) {
        MigrationRunEvidenceJpaEntity entity = new MigrationRunEvidenceJpaEntity();
        entity.id = id;
        return entity;
    }

    void replaceWith(
            MigrationRunEvidence value,
            JpaMigrationRunEvidenceRepository.MigrationRunEvidenceSerialized serialized) {
        if (recordedAt != null && value.recordedAt().isBefore(recordedAt.toInstant())) {
            throw new IllegalArgumentException(
                    "Migration run evidence cannot replace a newer revision.");
        }
        lifecycle = value.lifecycle();
        objectCountsJson = serialized.objectCounts();
        contentHashesJson = serialized.contentHashes();
        auditRefsJson = serialized.auditRefs();
        artifactRefsJson = serialized.artifactRefs();
        providerDiagnosticsJson = serialized.providerDiagnostics();
        identityMappingComplete = value.identityMappingComplete();
        auditSinkAvailable = value.auditSinkAvailable();
        adminApproved = value.adminApproved();
        recordedAt = value.recordedAt().atOffset(ZoneOffset.UTC);
        expiresAt = value.expiresAt() == null
                ? null
                : value.expiresAt().atOffset(ZoneOffset.UTC);
    }

    MigrationRunEvidence toDomain(
            Function<String, Map<String, Integer>> objectCountsReader,
            Function<String, List<String>> listReader,
            Function<String, Map<String, String>> stringMapReader) {
        return new MigrationRunEvidence(
                id.runId(),
                id.domainKey(),
                lifecycle,
                objectCountsReader.apply(objectCountsJson),
                listReader.apply(contentHashesJson),
                listReader.apply(auditRefsJson),
                stringMapReader.apply(artifactRefsJson),
                listReader.apply(providerDiagnosticsJson),
                identityMappingComplete,
                auditSinkAvailable,
                adminApproved,
                recordedAt.toInstant(),
                expiresAt == null ? null : expiresAt.toInstant());
    }
}

@Embeddable
class MigrationRunEvidenceId implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Column(name = "run_id", nullable = false, length = 180)
    private String runId;

    @Column(name = "domain_key", nullable = false, length = 120)
    private String domainKey;

    protected MigrationRunEvidenceId() {
    }

    MigrationRunEvidenceId(String runId, String domainKey) {
        this.runId = Objects.requireNonNull(runId, "runId");
        this.domainKey = Objects.requireNonNull(domainKey, "domainKey");
    }

    String runId() {
        return runId;
    }

    String domainKey() {
        return domainKey;
    }

    @Override
    public boolean equals(Object candidate) {
        return this == candidate
                || candidate instanceof MigrationRunEvidenceId other
                && Objects.equals(runId, other.runId)
                && Objects.equals(domainKey, other.domainKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(runId, domainKey);
    }
}

interface MigrationRunEvidenceJpaRepository
        extends JpaRepository<MigrationRunEvidenceJpaEntity, MigrationRunEvidenceId> {
}
