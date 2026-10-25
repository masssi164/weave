package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.EventRedactionLevel;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskBoardEvent;
import com.massimotter.weave.backend.boards.domain.TaskBoardEventType;
import com.massimotter.weave.backend.boards.port.BoardsRuntimeGuard;
import com.massimotter.weave.backend.boards.port.NoopTaskBoardEventPublisher;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import com.massimotter.weave.backend.boards.vikunja.VikunjaBoardsRepository;
import com.massimotter.weave.backend.boards.vikunja.VikunjaErrorMapper;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoardsWorkspaceContractTest {

    @Test
    void runtimeGuardFailsClosedWhenBoardsAreNotEnabled() {
        var guard = new BoardsRuntimeGuard(false);

        assertThat(guard.enabled()).isFalse();
        assertThatThrownBy(guard::requireEnabled)
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> assertThat(((BoardsException) error).code())
                        .isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE))
                .hasMessageContaining("feature-gated")
                .hasMessageContaining("runtime validation");
    }

    @Test
    void vikunjaRepositoryAdvertisesBoundaryCapabilitiesButStaysDisabled() {
        var repository = new VikunjaBoardsRepository();

        var capabilities = repository.capabilities();

        assertThat(capabilities.provider()).isEqualTo(ProviderKind.VIKUNJA);
        assertThat(capabilities.enabled()).isFalse();
        assertThat(capabilities.supported()).contains(
                BoardCapability.COMMENTS,
                BoardCapability.ATTACHMENTS,
                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                BoardCapability.WEBHOOK_EVENTS,
                BoardCapability.INCREMENTAL_SYNC,
                BoardCapability.CHECKLISTS,
                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES);
        assertThat(capabilities.unsupported()).contains(BoardCapability.CUSTOM_FIELDS);
        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> assertThat(((BoardsException) error).code())
                        .isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE))
                .hasMessageContaining("disabled")
                .hasMessageContaining("disabled");
    }

    @Test
    void vikunjaErrorsMapToSupportSafeWeaveCodes() {
        var mapper = new VikunjaErrorMapper();

        assertThat(mapper.toBoardsException(401, "list-projects").code()).isEqualTo(BoardsErrorCode.UNAUTHORIZED);
        assertThat(mapper.toBoardsException(403, "list-projects").code()).isEqualTo(BoardsErrorCode.FORBIDDEN);
        assertThat(mapper.toBoardsException(404, "find-task").code()).isEqualTo(BoardsErrorCode.NOT_FOUND);
        assertThat(mapper.toBoardsException(409, "move-task").code()).isEqualTo(BoardsErrorCode.CONFLICT);
        assertThat(mapper.toBoardsException(422, "create-task").code()).isEqualTo(BoardsErrorCode.VALIDATION);
        assertThat(mapper.toBoardsException(429, "sync").code()).isEqualTo(BoardsErrorCode.RATE_LIMITED);
        assertThat(mapper.toBoardsException(0, "sync").code()).isEqualTo(BoardsErrorCode.OFFLINE);
        assertThat(mapper.toBoardsException(503, "sync").code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
        assertThat(mapper.toBoardsException(418, "sync").code()).isEqualTo(BoardsErrorCode.UNKNOWN);
    }

    @Test
    void vikunjaErrorDetailsDoNotLeakRawProviderMessages() {
        var error = new VikunjaErrorMapper().toBoardsException(429, "sync");

        assertThat(error.getMessage()).contains("rate-limited");
        assertThat(error.details()).containsEntry("provider", "vikunja");
        assertThat(error.details()).containsEntry("operation", "sync");
        assertThat(error.details()).containsEntry("httpStatus", "429");
        assertThat(error.details()).doesNotContainKeys("rawMessage", "url", "token");
    }

    @Test
    void allSpecEventTypesRemainRepresentedInTheJavaContract() {
        assertThat(Arrays.stream(TaskBoardEventType.values()).map(TaskBoardEventType::contractName))
                .containsExactlyInAnyOrder(
                        "task.created",
                        "task.updated",
                        "task.completed",
                        "task.archived",
                        "task.moved",
                        "assignment.changed",
                        "label.changed",
                        "priority.changed",
                        "due_date.changed",
                        "comment.added",
                        "attachment.changed",
                        "sync.conflict_detected");
    }

    @Test
    void noopPublisherValidatesEventEnvelopeWithoutEnablingRuntimePublication() {
        var publisher = new NoopTaskBoardEventPublisher();
        var event = new TaskBoardEvent(
                "task-99:moved:2026-05-14T10:00:00Z",
                TaskBoardEventType.TASK_MOVED,
                "user:alice",
                Instant.parse("2026-05-14T10:00:00Z"),
                "workspace:weave",
                "project:launch",
                "board:launch",
                "task:99",
                null,
                EventRedactionLevel.SUPPORT_SAFE,
                Map.of("fromColumnId", "column:todo", "toColumnId", "column:doing"));

        assertThatCode(() -> publisher.publish(event)).doesNotThrowAnyException();
        assertThatThrownBy(() -> publisher.publish(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void workspaceOpenApiContractDeclaresUserWriteRoutes() throws Exception {
        String contract = Files.readString(Path.of("src/main/resources/contracts/boards-workspace.openapi.yaml"));

        assertThat(contract).contains("title: Weave Boards/Tasks Workspace Contract");
        assertThat(contract).contains("/api/boards/workspace");
        assertThat(contract).contains("/api/boards/{boardId}/tasks");
        assertThat(contract).contains("active-dogfood-production");
        assertThat(contract).contains("openproject-workspace-sync-backend-facade");
        assertThat(contract).contains("TaskBoardEvent");
        assertThat(contract).contains("task.moved");
    }

    @Test
    void eventJsonSchemaContainsProviderNeutralTypes() throws Exception {
        String schema = Files.readString(Path.of("src/main/resources/contracts/task-board-event.schema.json"));

        assertThat(schema).contains("normalized Boards/Tasks event envelope");
        assertThat(schema).contains("task.created");
        assertThat(schema).contains("sync.conflict_detected");
        assertThat(schema).contains("vikunja");
        assertThat(schema).contains("openproject");
        assertThat(schema).contains("nextcloud-deck");
    }
}
