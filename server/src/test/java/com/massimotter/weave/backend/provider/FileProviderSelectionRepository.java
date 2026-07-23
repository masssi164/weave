package com.massimotter.weave.backend.provider;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

public class FileProviderSelectionRepository implements ProviderSelectionRepository {

    private static final TypeReference<Map<String, ProviderSelection>> SELECTION_MAP = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Map<String, ProviderSelection> selections = new ConcurrentHashMap<>();
    private final Object persistenceLock = new Object();

    @Autowired
    public FileProviderSelectionRepository(
            ObjectMapper objectMapper,
            @Value("${weave.provider.selections.storage.path:./data/provider-selections.json}") String storagePath) {
        this(objectMapper, Path.of(storagePath));
    }

    FileProviderSelectionRepository(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
        load();
    }

    @Override
    public Optional<ProviderSelection> findByCategory(String category) {
        if (category == null || category.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(selections.get(normalizeCategory(category)));
    }

    @Override
    public List<ProviderSelection> findAll() {
        return selections.values().stream()
                .sorted(Comparator.comparing(ProviderSelection::category))
                .toList();
    }

    @Override
    public ProviderSelection save(ProviderSelection selection) {
        if (selection == null) {
            throw new IllegalArgumentException("Provider selection must not be null.");
        }
        synchronized (persistenceLock) {
            Map<String, ProviderSelection> nextSelections = new TreeMap<>(selections);
            nextSelections.put(normalizeCategory(selection.category()), selection);
            persist(nextSelections);
            selections.put(normalizeCategory(selection.category()), selection);
            return selection;
        }
    }

    @Override
    public String persistencePosture() {
        return "durable-file-backed";
    }

    private void load() {
        if (!Files.exists(storagePath)) {
            return;
        }
        try {
            Map<String, ProviderSelection> loaded = objectMapper.readValue(storagePath.toFile(), SELECTION_MAP);
            selections.clear();
            if (loaded != null) {
                loaded.values().forEach(selection -> selections.put(normalizeCategory(selection.category()), selection));
            }
        } catch (IOException exception) {
            throw new ProviderSelectionStoreException(
                    "Failed to load provider selections from " + storagePath, exception);
        }
    }

    private void persist(Map<String, ProviderSelection> nextSelections) {
        try {
            Path parent = storagePath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Path tempFile = Files.createTempFile(parent, storagePath.getFileName().toString(), ".tmp");
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile.toFile(), new TreeMap<>(nextSelections));
            try {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicMoveFailure) {
                Files.move(tempFile, storagePath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw new ProviderSelectionStoreException(
                    "Failed to persist provider selections to " + storagePath, exception);
        }
    }

    private String normalizeCategory(String category) {
        return category.trim().toLowerCase(Locale.ROOT);
    }
}
