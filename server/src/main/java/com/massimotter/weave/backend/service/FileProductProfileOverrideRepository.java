package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.massimotter.weave.backend.model.IdentityKeyFormat;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

@Repository
public class FileProductProfileOverrideRepository implements ProductProfileOverrideRepository {

    private static final TypeReference<Map<String, ProductProfileOverride>> PROFILE_MAP = new TypeReference<>() {
    };
    private static final Pattern PRIMARY_IDENTITY_KEY_PATTERN = Pattern.compile(IdentityKeyFormat.PRIMARY_IDENTITY_KEY_PATTERN);

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<String, ProductProfileOverride> profiles = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    @Autowired
    public FileProductProfileOverrideRepository(
            ObjectMapper objectMapper,
            @Value("${weave.profile.storage.path:./data/profile-overrides.json}") String storagePath) {
        this(objectMapper, Path.of(storagePath));
    }

    FileProductProfileOverrideRepository(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        load();
    }

    @Override
    public ProductProfileOverride findByPrimaryIdentityKey(String primaryIdentityKey) {
        ProductProfileOverride profile = profiles.get(primaryIdentityKey);
        if (profile != null) {
            return profile;
        }
        return migrateLegacySubjectProfile(primaryIdentityKey);
    }

    @Override
    public ProductProfileOverride saveForPrimaryIdentityKey(String primaryIdentityKey, ProductProfileOverride profile) {
        synchronized (persistenceLock) {
            profiles.put(primaryIdentityKey, profile);
            persist();
            return profile;
        }
    }

    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            Map<String, ProductProfileOverride> loaded = objectMapper.readValue(storagePath.toFile(), PROFILE_MAP);
            profiles.clear();
            if (loaded != null) {
                profiles.putAll(loaded);
            }
        } catch (IOException exception) {
            throw new ProductProfileStoreException(
                    "Failed to load product profile overrides from " + storagePath, exception);
        }
    }

    private ProductProfileOverride migrateLegacySubjectProfile(String primaryIdentityKey) {
        if (primaryIdentityKey == null || !PRIMARY_IDENTITY_KEY_PATTERN.matcher(primaryIdentityKey).matches()) {
            return null;
        }
        String legacySubjectKey = primaryIdentityKey.substring(primaryIdentityKey.lastIndexOf('#') + 1);
        ProductProfileOverride legacyProfile = profiles.get(legacySubjectKey);
        if (legacyProfile == null) {
            return null;
        }
        synchronized (persistenceLock) {
            ProductProfileOverride current = profiles.get(primaryIdentityKey);
            if (current != null) {
                return current;
            }
            ProductProfileOverride migrated = profiles.remove(legacySubjectKey);
            if (migrated == null) {
                return null;
            }
            profiles.put(primaryIdentityKey, migrated);
            persist();
            return migrated;
        }
    }

    private void persist() {
        try {
            Path parent = storagePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, storagePath.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), new TreeMap<>(profiles));
            try {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProductProfileStoreException(
                    "Failed to persist product profile overrides to " + storagePath, exception);
        }
    }
}
