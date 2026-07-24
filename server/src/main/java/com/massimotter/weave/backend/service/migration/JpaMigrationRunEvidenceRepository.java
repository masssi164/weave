package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.PersistenceException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import static java.util.Objects.requireNonNull;

/** Versioned relational adapter for support-safe provider migration evidence. */
@Repository
@Transactional(readOnly = true)
public class JpaMigrationRunEvidenceRepository implements MigrationRunEvidenceRepository {

    private static final TypeReference<Map<String, Integer>> OBJECT_COUNTS =
            new TypeReference<>() {
            };
    private static final TypeReference<List<String>> STRING_LIST =
            new TypeReference<>() {
            };
    private static final TypeReference<Map<String, String>> STRING_MAP =
            new TypeReference<>() {
            };

    private final MigrationRunEvidenceJpaRepository evidence;
    private final ObjectMapper objectMapper;

    public JpaMigrationRunEvidenceRepository(
            MigrationRunEvidenceJpaRepository evidence,
            ObjectMapper objectMapper) {
        this.evidence = requireNonNull(evidence, "evidence");
        this.objectMapper = requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    @Transactional
    public void save(MigrationRunEvidence value) {
        if (value == null) {
            throw new IllegalArgumentException("Migration run evidence must not be null.");
        }
        if (value.recordedAt() == null) {
            throw new IllegalArgumentException(
                    "Migration run evidence recordedAt must not be null.");
        }
        try {
            MigrationRunEvidenceId id =
                    new MigrationRunEvidenceId(value.runId(), value.domainKey());
            MigrationRunEvidenceJpaEntity entity = evidence.findById(id)
                    .orElseGet(() -> MigrationRunEvidenceJpaEntity.create(id));
            entity.replaceWith(value, serialized(value));
            evidence.saveAndFlush(entity);
        } catch (MigrationRunEvidenceStoreException exception) {
            throw exception;
        } catch (DataAccessException | PersistenceException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to persist durable migration run evidence.",
                    exception);
        }
    }

    @Override
    public Optional<MigrationRunEvidence> findCurrent(
            String runId,
            String domainKey,
            Instant now) {
        try {
            return evidence.findById(new MigrationRunEvidenceId(runId, domainKey))
                    .map(this::toDomain)
                    .filter(value -> !value.expired(now));
        } catch (MigrationRunEvidenceStoreException exception) {
            throw exception;
        } catch (DataAccessException | PersistenceException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to load durable migration run evidence.",
                    exception);
        }
    }

    String persistencePosture() {
        return "durable-relational-jpa-flyway";
    }

    private MigrationRunEvidence toDomain(MigrationRunEvidenceJpaEntity entity) {
        return entity.toDomain(
                json -> read(json, OBJECT_COUNTS, Map.of()),
                json -> read(json, STRING_LIST, List.of()),
                json -> read(json, STRING_MAP, Map.of()));
    }

    private MigrationRunEvidenceSerialized serialized(MigrationRunEvidence value) {
        return new MigrationRunEvidenceSerialized(
                json(value.objectCounts()),
                json(value.contentHashes()),
                json(value.auditRefs()),
                json(value.artifactRefs()),
                json(value.providerDiagnostics()));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to persist durable migration run evidence.",
                    exception);
        }
    }

    private <T> T read(String json, TypeReference<T> type, T fallback) {
        if (json == null || json.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to load durable migration run evidence.",
                    exception);
        }
    }

    record MigrationRunEvidenceSerialized(
            String objectCounts,
            String contentHashes,
            String auditRefs,
            String artifactRefs,
            String providerDiagnostics) {
    }
}
