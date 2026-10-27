package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.domain.EventRedactionLevel;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskBoardEventType;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.events.TaskBoardEventInput;
import com.massimotter.weave.backend.boards.events.TaskBoardEventNormalizer;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TaskBoardEventNormalizerTest {

    private final TaskBoardEventNormalizer normalizer = new TaskBoardEventNormalizer();

    @Test
    void normalizesVikunjaMoveWebhookIntoProviderNeutralEnvelope() {
        var event = normalizer.normalize(new TaskBoardEventInput(
                "webhook-42",
                TaskBoardEventType.TASK_MOVED,
                "user:alice",
                Instant.parse("2026-05-14T11:00:00Z"),
                "workspace:weave",
                "vikunja:project:7",
                "vikunja:board:7",
                "vikunja:task:99",
                new ProviderRef(
                        ProviderKind.VIKUNJA,
                        "task:99",
                        URI.create("https://tasks.example.invalid/tasks/99"),
                        "42",
                        "etag-42",
                        Instant.parse("2026-05-14T11:00:00Z")),
                EventRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "fromColumnId", "vikunja:column:1",
                        "toColumnId", "vikunja:column:2",
                        "position", 3,
                        "statusAfter", TaskStatus.OPEN)));

        assertThat(event.idempotencyKey()).isEqualTo("vikunja:webhook-42");
        assertThat(event.type()).isEqualTo(TaskBoardEventType.TASK_MOVED);
        assertThat(event.providerRef().provider()).isEqualTo(ProviderKind.VIKUNJA);
        assertThat(event.payload()).containsEntry("sourceProvider", "vikunja");
        assertThat(event.payload()).containsEntry("eventType", "task.moved");
        assertThat(event.payload()).containsEntry("fromColumnId", "vikunja:column:1");
        assertThat(event.payload()).containsEntry("toColumnId", "vikunja:column:2");
        assertThat(event.payload()).containsEntry("statusAfter", "open");
    }

    @Test
    void normalizesOpenProjectActivityWithoutBindingToActionBoardVocabulary() {
        var event = normalizer.normalize(new TaskBoardEventInput(
                "activity-987",
                TaskBoardEventType.DUE_DATE_CHANGED,
                "openproject:user:12",
                Instant.parse("2026-05-14T12:00:00Z"),
                "workspace:weave",
                "openproject:project:5",
                "openproject:board:11",
                "openproject:work-package:321",
                ProviderRef.of(ProviderKind.OPEN_PROJECT, "work_package:321"),
                EventRedactionLevel.WORKSPACE_INTERNAL,
                Map.of(
                        "providerObjectKind", "work_package",
                        "dueDateBefore", "2026-05-20",
                        "dueDateAfter", "2026-05-27")));

        assertThat(event.idempotencyKey()).isEqualTo("openproject:activity-987");
        assertThat(event.type().contractName()).isEqualTo("due_date.changed");
        assertThat(event.payload()).containsEntry("providerObjectKind", "work_package");
        assertThat(event.payload()).doesNotContainKey("actionBoardColumnName");
    }

    @Test
    void normalizesDeckPollingChangeWithDeterministicFallbackIdempotencyKey() {
        var event = normalizer.normalize(new TaskBoardEventInput(
                null,
                TaskBoardEventType.LABEL_CHANGED,
                "nextcloud:user:admin",
                Instant.parse("2026-05-14T13:00:00Z"),
                "workspace:weave",
                "deck:board:10",
                "deck:board:10",
                "deck:card:81",
                ProviderRef.of(ProviderKind.NEXTCLOUD_DECK, "card:81"),
                EventRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "labelRefsBefore", List.of("deck:label:37"),
                        "labelRefsAfter", List.of("deck:label:37", "deck:label:39"),
                        "priorityAfter", TaskPriority.HIGH)));

        assertThat(event.idempotencyKey())
                .isEqualTo("nextcloud-deck:card:81:label.changed:2026-05-14T13:00:00Z");
        assertThat(event.payload()).containsEntry("sourceProvider", "nextcloud-deck");
        assertThat(event.payload()).containsEntry("priorityAfter", "high");
        assertThat(event.payload().get("labelRefsAfter")).isEqualTo(List.of("deck:label:37", "deck:label:39"));
    }

    @Test
    void redactsSensitiveProviderDetailsFromSupportSafePayloads() {
        var event = normalizer.normalize(new TaskBoardEventInput(
                "conflict-1",
                TaskBoardEventType.SYNC_CONFLICT_DETECTED,
                "system:sync",
                Instant.parse("2026-05-14T14:00:00Z"),
                "workspace:weave",
                "project:1",
                "board:1",
                "task:1",
                ProviderRef.of(ProviderKind.VIKUNJA, "task:1"),
                EventRedactionLevel.SUPPORT_SAFE,
                Map.of(
                        "rawMessage", "Bearer token leaked by provider",
                        "requestUrl", "https://tasks.example.invalid/tasks/1?token=secret",
                        "authorization", "Bearer abc",
                        "token", "abc",
                        "supportSafeReason", "etag_conflict",
                        "nested", Map.of("clientSecret", "shh", "attempt", 2))));

        assertThat(event.payload()).containsEntry("rawMessage", "[redacted]");
        assertThat(event.payload()).containsEntry("requestUrl", "[redacted]");
        assertThat(event.payload()).containsEntry("authorization", "[redacted]");
        assertThat(event.payload()).containsEntry("token", "[redacted]");
        assertThat(event.payload()).containsEntry("supportSafeReason", "etag_conflict");
        assertThat(event.payload().get("nested")).isEqualTo(Map.of("attempt", 2, "clientSecret", "[redacted]"));
    }

    @Test
    void validatesRequiredEnvelopeFieldsBeforePublication() {
        assertThatThrownBy(() -> new TaskBoardEventInput(
                "event-1",
                TaskBoardEventType.TASK_CREATED,
                " ",
                Instant.parse("2026-05-14T15:00:00Z"),
                "workspace:weave",
                null,
                null,
                null,
                ProviderRef.of(ProviderKind.UNKNOWN, "event:1"),
                EventRedactionLevel.PUBLIC_METADATA,
                Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actorRef");
    }
}
