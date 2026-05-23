package com.massimotter.weave.backend.boards.events;

import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.EventRedactionLevel;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskBoardEvent;
import com.massimotter.weave.backend.boards.domain.TaskBoardEventType;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts provider-specific activity records into the provider-neutral Weave
 * Boards/Tasks event envelope. This is intentionally backend-owned preview code:
 * Flutter consumes Weave task state/events, never Vikunja/OpenProject/Deck payloads.
 */
public final class TaskBoardEventNormalizer {

    private static final String REDACTED = "[redacted]";

    public TaskBoardEvent normalize(TaskBoardEventInput input) {
        Map<String, Object> payload = supportSafePayload(input);
        return new TaskBoardEvent(
                idempotencyKey(input),
                input.type(),
                input.actorRef(),
                input.occurredAt(),
                input.workspaceId(),
                input.projectId(),
                input.boardId(),
                input.taskId(),
                input.providerRef(),
                input.redactionLevel(),
                payload);
    }

    private String idempotencyKey(TaskBoardEventInput input) {
        ProviderRef ref = input.providerRef();
        String provider = providerName(ref);
        if (hasText(input.sourceEventId())) {
            return provider + ":" + input.sourceEventId().trim();
        }
        String externalId = ref == null ? null : ref.externalId();
        if (hasText(externalId)) {
            return String.join(":",
                    provider,
                    externalId.trim(),
                    input.type().contractName(),
                    input.occurredAt().toString());
        }
        return String.join(":",
                input.workspaceId(),
                valueOrUnknown(input.taskId()),
                input.type().contractName(),
                input.occurredAt().toString());
    }

    private Map<String, Object> supportSafePayload(TaskBoardEventInput input) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sourceProvider", providerName(input.providerRef()));
        if (hasText(input.sourceEventId())) {
            payload.put("sourceEventId", input.sourceEventId().trim());
        }
        payload.put("eventType", input.type().contractName());
        input.payload().entrySet().stream()
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .filter(entry -> entry.getValue() != null)
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> payload.put(entry.getKey(), sanitizeValue(entry.getKey(), entry.getValue(), input.redactionLevel())));
        return Map.copyOf(payload);
    }

    private Object sanitizeValue(String key, Object value, EventRedactionLevel redactionLevel) {
        if (value == null) {
            return null;
        }
        if (isSensitiveKey(key) && redactionLevel != EventRedactionLevel.PRIVATE_CONTENT) {
            return REDACTED;
        }
        return sanitizeObject(value, redactionLevel);
    }

    private Object sanitizeObject(Object value, EventRedactionLevel redactionLevel) {
        if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Instant instant) {
            return instant.toString();
        }
        if (value instanceof TaskBoardEventType eventType) {
            return eventType.contractName();
        }
        if (value instanceof ProviderKind providerKind) {
            return providerKind.contractName();
        }
        if (value instanceof TaskStatus taskStatus) {
            return taskStatus.contractName();
        }
        if (value instanceof ColumnSemanticStatus columnStatus) {
            return columnStatus.contractName();
        }
        if (value instanceof TaskPriority taskPriority) {
            return taskPriority.name().toLowerCase();
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitized = new LinkedHashMap<>();
            map.entrySet().stream()
                    .filter(entry -> entry.getKey() instanceof String && !((String) entry.getKey()).isBlank())
                    .sorted((left, right) -> ((String) left.getKey()).compareTo((String) right.getKey()))
                    .forEach(entry -> sanitized.put(
                            (String) entry.getKey(),
                            sanitizeValue((String) entry.getKey(), entry.getValue(), redactionLevel)));
            return Map.copyOf(sanitized);
        }
        if (value instanceof Iterable<?> iterable) {
            List<Object> sanitized = new ArrayList<>();
            for (Object item : iterable) {
                sanitized.add(sanitizeObject(item, redactionLevel));
            }
            return List.copyOf(sanitized);
        }
        return value.toString();
    }

    private boolean isSensitiveKey(String key) {
        String normalized = key.toLowerCase().replace("_", "").replace("-", "");
        return normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("authorization")
                || normalized.contains("credential")
                || normalized.contains("rawmessage")
                || normalized.equals("url")
                || normalized.endsWith("url");
    }

    private String providerName(ProviderRef ref) {
        return ref == null ? ProviderKind.UNKNOWN.contractName() : ref.provider().contractName();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String valueOrUnknown(String value) {
        return hasText(value) ? value.trim() : "unknown-task";
    }
}
