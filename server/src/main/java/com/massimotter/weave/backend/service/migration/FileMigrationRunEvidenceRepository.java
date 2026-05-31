package com.massimotter.weave.backend.service.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
class FileMigrationRunEvidenceRepository implements MigrationRunEvidenceRepository {

    private static final TypeReference<Map<String, MigrationRunEvidence>> EVIDENCE_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<String, MigrationRunEvidence> evidenceByRunAndDomain = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    @Autowired
    FileMigrationRunEvidenceRepository(
            ObjectMapper objectMapper,
            @Value("${weave.migration.evidence.storage.path:./data/migration-run-evidence.json}") String storagePath) {
        this(objectMapper, Path.of(storagePath));
    }

    FileMigrationRunEvidenceRepository(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        load();
    }

    @Override
    public void save(MigrationRunEvidence evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Migration run evidence must not be null.");
        }
        synchronized (persistenceLock) {
            evidenceByRunAndDomain.put(key(evidence.runId(), evidence.domainKey()), evidence);
            persist();
        }
    }

    @Override
    public Optional<MigrationRunEvidence> findCurrent(String runId, String domainKey, Instant now) {
        return Optional.ofNullable(evidenceByRunAndDomain.get(key(runId, domainKey)))
                .filter(evidence -> !evidence.expired(now));
    }

    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            Map<String, MigrationRunEvidence> loaded = objectMapper.readValue(storagePath.toFile(), EVIDENCE_MAP);
            evidenceByRunAndDomain.clear();
            if (loaded != null) {
                loaded.values().forEach(evidence -> evidenceByRunAndDomain.put(key(evidence.runId(), evidence.domainKey()), evidence));
            }
        } catch (IOException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to load migration run evidence from " + storagePath, exception);
        }
    }

    private void persist() {
        try {
            Path parent = storagePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, storagePath.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), new TreeMap<>(evidenceByRunAndDomain));
            try {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new MigrationRunEvidenceStoreException(
                    "Failed to persist migration run evidence to " + storagePath, exception);
        }
    }

    private String key(String runId, String domainKey) {
        return runId + "::" + domainKey;
    }
}
