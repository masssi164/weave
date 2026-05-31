package com.massimotter.weave.backend.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static java.util.Objects.requireNonNull;

/**
 * Durable append-only audit sink for server-side control-plane and provider mutation gates.
 */
public final class FileAuditEventPublisher implements AuditEventPublisher {

    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final Object appendLock = new Object();

    @Autowired
    public FileAuditEventPublisher(
            ObjectMapper objectMapper,
            @Value("${weave.audit.events.storage.path:./data/audit-events.jsonl}") String storagePath) {
        this(objectMapper, Path.of(storagePath));
    }

    public FileAuditEventPublisher(ObjectMapper objectMapper, Path storagePath) {
        this.objectMapper = objectMapper;
        this.storagePath = storagePath;
    }

    @Override
    public void publish(AuditEvent event) {
        requireNonNull(event, "event must not be null");
        synchronized (appendLock) {
            try {
                Path parent = storagePath.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                String line = objectMapper.writeValueAsString(event) + System.lineSeparator();
                Files.writeString(
                        storagePath,
                        line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } catch (IOException exception) {
                throw new AuditRequiredException("durable audit publication failed for " + storagePath, exception);
            }
        }
    }

    public List<AuditEvent> events() {
        synchronized (appendLock) {
            if (!Files.exists(storagePath)) {
                return List.of();
            }
            try {
                return Files.readAllLines(storagePath, StandardCharsets.UTF_8).stream()
                        .filter(line -> line != null && !line.isBlank())
                        .map(this::readEvent)
                        .toList();
            } catch (IOException exception) {
                throw new AuditRequiredException("durable audit read failed for " + storagePath, exception);
            }
        }
    }

    private AuditEvent readEvent(String line) {
        try {
            return objectMapper.readValue(line, AuditEvent.class);
        } catch (IOException exception) {
            throw new AuditRequiredException("durable audit event was not readable", exception);
        }
    }
}
