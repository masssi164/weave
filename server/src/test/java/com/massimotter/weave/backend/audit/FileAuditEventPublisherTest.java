package com.massimotter.weave.backend.audit;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

class FileAuditEventPublisherTest {

    @TempDir
    Path tempDir;

    @Test
    void auditEventsAppendToDurableJsonLinesSink() throws Exception {
        Path storagePath = tempDir.resolve("audit-events.jsonl");
        ObjectMapper objectMapper = tools.jackson.databind.json.JsonMapper.builder().findAndAddModules().build();
        var publisher = new FileAuditEventPublisher(objectMapper, storagePath);

        publisher.publish(new AuditEvent(
                "weave-dogfood",
                "admin-control-plane",
                "user:admin-123",
                "provider-selection",
                AuditAction.ADMIN_POLICY_UPDATED,
                Instant.parse("2026-05-31T08:00:00Z"),
                "provider-selection-chat-001",
                AuditRedactionLevel.SECRET_REDACTED,
                Map.of("category", "chat", "supportSafe", true)));

        assertThat(storagePath).exists();
        assertThat(Files.readAllLines(storagePath)).hasSize(1);
        JsonNode event = objectMapper.readTree(Files.readString(storagePath));
        assertThat(event.path("tenantId").asText()).isEqualTo("weave-dogfood");
        assertThat(event.path("payload").path("category").asText()).isEqualTo("chat");
        assertThat(publisher.events()).hasSize(1);
    }
}
