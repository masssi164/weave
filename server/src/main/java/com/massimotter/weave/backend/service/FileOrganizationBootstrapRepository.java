package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileOrganizationBootstrapRepository implements OrganizationBootstrapRepository {

    private static final TypeReference<Map<String, OrganizationBootstrapRecord>> BOOTSTRAP_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<String, OrganizationBootstrapRecord> records = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    @Autowired
    public FileOrganizationBootstrapRepository(
            ObjectMapper objectMapper,
            @Value("${weave.organization.bootstrap.storage.path:./data/organization-bootstrap.json}") String storagePath) {
        this(objectMapper, Path.of(storagePath));
    }

    FileOrganizationBootstrapRepository(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        load();
    }

    @Override
    public Optional<OrganizationBootstrapRecord> findByOrganizationId(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(records.get(normalizeOrganizationId(organizationId)));
    }

    @Override
    public OrganizationBootstrapRecord save(OrganizationBootstrapRecord record) {
        if (record == null || record.organizationId() == null || record.organizationId().isBlank()) {
            throw new IllegalArgumentException("Organization bootstrap record requires a non-blank organization id.");
        }
        synchronized (persistenceLock) {
            records.put(normalizeOrganizationId(record.organizationId()), record);
            persist();
            return record;
        }
    }

    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            Map<String, OrganizationBootstrapRecord> loaded = objectMapper.readValue(storagePath.toFile(), BOOTSTRAP_MAP);
            records.clear();
            if (loaded != null) {
                loaded.values().forEach(record -> records.put(normalizeOrganizationId(record.organizationId()), record));
            }
        } catch (IOException exception) {
            throw new OrganizationBootstrapStoreException(
                    "Failed to load organization bootstrap records from " + storagePath, exception);
        }
    }

    private void persist() {
        try {
            Path parent = storagePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, storagePath.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), new TreeMap<>(records));
            try {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new OrganizationBootstrapStoreException(
                    "Failed to persist organization bootstrap records to " + storagePath, exception);
        }
    }

    private String normalizeOrganizationId(String organizationId) {
        return organizationId.trim().toLowerCase(Locale.ROOT);
    }
}
