package com.massimotter.weave.backend.service.migration;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaEntity;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceId;
import com.massimotter.weave.backend.persistence.jpa.migration.MigrationRunEvidenceJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.annotation.Transactional;

public class JpaMigrationRunEvidenceRepository implements MigrationRunEvidenceRepository {

    private static final TypeReference<Map<String, Integer>> OBJECT_COUNTS = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {
    };

    private final MigrationRunEvidenceJpaRepository repository;
    private final ObjectMapper objectMapper;

    public JpaMigrationRunEvidenceRepository(
            MigrationRunEvidenceJpaRepository repository,
            ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void save(MigrationRunEvidence evidence) {
        if (evidence == null || evidence.recordedAt() == null) {
            throw new IllegalArgumentException("Migration run evidence and recordedAt are required.");
        }
        try {
            repository.saveAndFlush(toEntity(evidence));
        } catch (DataAccessException failure) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to persist durable migration run evidence.", failure);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now) {
        try {
            return repository.findById(new MigrationRunEvidenceId(runId, domainKey))
                    .map(this::toDomain)
                    .filter(evidence -> !evidence.expired(now));
        } catch (DataAccessException failure) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to load durable migration run evidence.", failure);
        }
    }

    String persistencePosture() {
        return "portable-jpa-hibernate-validated";
    }

    private MigrationRunEvidenceJpaEntity toEntity(MigrationRunEvidence value) {
        return new MigrationRunEvidenceJpaEntity(
                new MigrationRunEvidenceId(value.runId(), value.domainKey()),
                value.lifecycle(),
                json(value.objectCounts()),
                json(value.contentHashes()),
                json(value.auditRefs()),
                json(value.artifactRefs()),
                json(value.providerDiagnostics()),
                value.identityMappingComplete(),
                value.auditSinkAvailable(),
                value.adminApproved(),
                value.recordedAt(),
                value.expiresAt());
    }

    private MigrationRunEvidence toDomain(MigrationRunEvidenceJpaEntity value) {
        return new MigrationRunEvidence(
                value.id().runId(),
                value.id().domainKey(),
                value.lifecycle(),
                read(value.objectCountsJson(), OBJECT_COUNTS, Map.of()),
                read(value.contentHashesJson(), STRING_LIST, List.of()),
                read(value.auditRefsJson(), STRING_LIST, List.of()),
                read(value.artifactRefsJson(), STRING_MAP, Map.of()),
                read(value.providerDiagnosticsJson(), STRING_LIST, List.of()),
                value.identityMappingComplete(),
                value.auditSinkAvailable(),
                value.adminApproved(),
                value.recordedAt(),
                value.expiresAt());
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JacksonException failure) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to persist durable migration run evidence.", failure);
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException failure) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to load durable migration run evidence.", failure);
        }
    }
}
