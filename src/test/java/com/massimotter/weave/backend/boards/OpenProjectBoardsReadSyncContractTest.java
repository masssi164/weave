package com.massimotter.weave.backend.boards;

import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsMapper;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRepository;
import com.massimotter.weave.backend.boards.openproject.OpenProjectBoardsRuntimeGate;
import com.massimotter.weave.backend.boards.openproject.OpenProjectProjectSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectStatusSnapshot;
import com.massimotter.weave.backend.boards.openproject.OpenProjectWorkPackageSnapshot;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenProjectBoardsReadSyncContractTest {

    @Test
    void openProjectIsPreferredReadSyncSeamButRuntimeStaysDisabled() {
        var repository = new OpenProjectBoardsRepository();

        var capabilities = repository.capabilities();

        assertThat(capabilities.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
        assertThat(capabilities.enabled()).isFalse();
        assertThat(capabilities.supported()).contains(
                BoardCapability.COMMENTS,
                BoardCapability.ATTACHMENTS,
                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                BoardCapability.INCREMENTAL_SYNC,
                BoardCapability.CUSTOM_FIELDS,
                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES);
        assertThat(capabilities.unsupported()).contains(BoardCapability.WEBHOOK_EVENTS, BoardCapability.CHECKLISTS);
        assertThat(capabilities.supportSafeSummary())
                .contains("OpenProject")
                .contains("read-only-first")
                .contains("fallback");
        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
                    assertThat(boardsError.details()).containsEntry("provider", "openproject");
                    assertThat(boardsError.details().get("missingGates"))
                            .contains("provider_runtime")
                            .contains("read_sync")
                            .contains("context_authorization");
                })
                .hasMessageContaining("read-sync")
                .hasMessageContaining("fail-closed");
    }

    @Test
    void openProjectGateRequiresContextAuthorizationBeforeReadSync() {
        var repository = new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                true,
                true,
                false,
                false,
                false,
                "service-account"));

        assertThatThrownBy(() -> repository.listProjects(null))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.PROVIDER_UNAVAILABLE);
                    assertThat(boardsError.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "list-projects")
                            .containsEntry("mode", "read_sync");
                    assertThat(boardsError.details().get("missingGates"))
                            .contains("context_authorization")
                            .doesNotContain("provider_runtime")
                            .doesNotContain("read_sync");
                });
    }

    @Test
    void openProjectWritesStayUnsupportedUntilAuditConsentPromotion() {
        var repository = new OpenProjectBoardsRepository(new OpenProjectBoardsRuntimeGate(
                true,
                true,
                true,
                false,
                false,
                "service-account"));

        assertThatThrownBy(() -> repository.completeTask("openproject:work-package:99"))
                .isInstanceOf(BoardsException.class)
                .satisfies(error -> {
                    var boardsError = (BoardsException) error;
                    assertThat(boardsError.code()).isEqualTo(BoardsErrorCode.UNSUPPORTED_CAPABILITY);
                    assertThat(boardsError.details())
                            .containsEntry("provider", "openproject")
                            .containsEntry("operation", "complete-task")
                            .containsEntry("mode", "write")
                            .containsEntry("missingGates", "provider_writes_disabled")
                            .containsEntry("providerWritesEnabled", "false");
                })
                .hasMessageContaining("writes remain disabled")
                .hasMessageContaining("audit");
    }

    @Test
    void mapperTurnsOpenProjectProjectsStatusesAndWorkPackagesIntoWeaveBoards() {
        var mapper = new OpenProjectBoardsMapper();
        Instant syncedAt = Instant.parse("2026-05-18T19:45:00Z");
        var project = new OpenProjectProjectSnapshot(
                42,
                "apollo",
                "Apollo Launch",
                "Imported from OpenProject read sync.",
                false,
                URI.create("https://openproject.example.test/projects/apollo"),
                syncedAt);
        var statusNew = new OpenProjectStatusSnapshot(1, "New", 0, false, null);
        var statusBlocked = new OpenProjectStatusSnapshot(2, "Blocked", 1, false, null);
        var statusClosed = new OpenProjectStatusSnapshot(3, "Closed", 2, true, null);

        var columns = List.of(
                mapper.toColumn(project.id(), statusNew),
                mapper.toColumn(project.id(), statusBlocked),
                mapper.toColumn(project.id(), statusClosed));
        var board = mapper.toBoard(project, columns);
        var task = mapper.toTask(new OpenProjectWorkPackageSnapshot(
                        99,
                        project.id(),
                        statusClosed.id(),
                        "Ship backend seam",
                        "Read-only first; no provider writes.",
                        7,
                        "High",
                        List.of("user:massimo"),
                        List.of("label:contract"),
                        null,
                        Instant.parse("2026-06-01T00:00:00Z"),
                        Instant.parse("2026-05-19T08:00:00Z"),
                        syncedAt,
                        URI.create("https://openproject.example.test/work_packages/99"),
                        "17"),
                Map.of(statusNew.id(), statusNew, statusBlocked.id(), statusBlocked, statusClosed.id(), statusClosed));

        assertThat(mapper.toProject(project).id()).isEqualTo("openproject:project:42");
        assertThat(board.id()).isEqualTo("openproject:board:42");
        assertThat(board.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
            assertThat(ref.externalId()).isEqualTo("project:42");
        });
        assertThat(columns).extracting("semanticStatus")
                .containsExactly(ColumnSemanticStatus.NOT_STARTED, ColumnSemanticStatus.BLOCKED, ColumnSemanticStatus.DONE);
        assertThat(task.id()).isEqualTo("openproject:work-package:99");
        assertThat(task.boardId()).isEqualTo("openproject:board:42");
        assertThat(task.columnId()).isEqualTo("openproject:status:3");
        assertThat(task.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(task.priority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.providerRefs()).singleElement().satisfies(ref -> {
            assertThat(ref.provider()).isEqualTo(ProviderKind.OPEN_PROJECT);
            assertThat(ref.externalId()).isEqualTo("work-package:99");
            assertThat(ref.version()).isEqualTo("17");
            assertThat(ref.lastSyncedAt()).isEqualTo(syncedAt);
        });
    }
}
