package com.massimotter.weave.backend.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
public class FileBackedWeaverRuntimeRevocationStore implements WeaverRuntimeRevocationStore {

    private final Path storePath;
    private final ObjectMapper objectMapper;

    public FileBackedWeaverRuntimeRevocationStore(String storePath) {
        this(Path.of(storePath));
    }

    FileBackedWeaverRuntimeRevocationStore(Path storePath) {
        this.storePath = storePath;
        this.objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Override
    public synchronized List<RevocationRecord> recordsForUser(String userRef) {
        return readAll().stream()
                .filter(record -> record.userRef().equals(userRef))
                .toList();
    }

    @Override
    public synchronized Optional<RevocationRecord> recordForProfile(String runtimeProfileHash) {
        return readAll().stream()
                .filter(record -> record.runtimeProfileHash().equals(runtimeProfileHash))
                .findFirst();
    }

    @Override
    public synchronized void record(RevocationRecord record) {
        List<RevocationRecord> records = new ArrayList<>(readAll());
        records.removeIf(existing -> existing.runtimeProfileHash().equals(record.runtimeProfileHash()));
        records.add(record);
        writeAll(records);
    }

    private List<RevocationRecord> readAll() {
        if (!Files.exists(storePath)) {
            return List.of();
        }
        try {
            List<Map<String, Object>> rows = objectMapper.readValue(storePath.toFile(), new TypeReference<>() {});
            return rows.stream().map(this::fromMap).toList();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Weaver runtime revocation store is unavailable; refusing to trust runtime profiles.", exception);
        }
    }

    private void writeAll(List<RevocationRecord> records) {
        try {
            Path parent = storePath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            List<Map<String, Object>> rows = records.stream().map(this::toMap).toList();
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(storePath.toFile(), rows);
        } catch (IOException exception) {
            throw new UncheckedIOException("Weaver runtime revocation store is unavailable; refusing to trust runtime profiles.", exception);
        }
    }

    private Map<String, Object> toMap(RevocationRecord record) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("userRef", record.userRef());
        row.put("runtimeProfileHash", record.runtimeProfileHash());
        row.put("signature", record.signature());
        row.put("revocationGeneration", record.revocationGeneration());
        row.put("reason", record.reason());
        row.put("actor", record.actor());
        row.put("scope", record.scope());
        row.put("revokedAt", record.revokedAt().toString());
        row.put("evidenceRef", record.evidenceRef());
        return row;
    }

    private RevocationRecord fromMap(Map<String, Object> row) {
        return new RevocationRecord(
                String.valueOf(row.get("userRef")),
                String.valueOf(row.get("runtimeProfileHash")),
                String.valueOf(row.get("signature")),
                ((Number) row.getOrDefault("revocationGeneration", 0)).intValue(),
                String.valueOf(row.get("reason")),
                String.valueOf(row.getOrDefault("actor", "system:weaver-runtime-policy")),
                String.valueOf(row.getOrDefault("scope", "runtime-profile")),
                Instant.parse(String.valueOf(row.get("revokedAt"))),
                String.valueOf(row.get("evidenceRef")));
    }
}
