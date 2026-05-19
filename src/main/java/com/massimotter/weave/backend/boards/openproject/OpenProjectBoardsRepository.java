package com.massimotter.weave.backend.boards.openproject;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.Label;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.TaskAttachment;
import com.massimotter.weave.backend.boards.domain.TaskComment;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.port.BoardPage;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;

import java.util.EnumSet;
import java.util.Optional;

/**
 * Disabled OpenProject-first read-sync adapter contract. OpenProject is the
 * preferred first provider-backed validation target because its API can map
 * projects/statuses/work packages into Weave Boards without making provider
 * vocabulary the product model. Runtime HTTP calls remain disabled until a later
 * promotion spec defines auth, API filters, synchronization, and route DTOs.
 */
public final class OpenProjectBoardsRepository implements BoardsRepository {

    private final OpenProjectBoardsRuntimeGate runtimeGate;

    public OpenProjectBoardsRepository() {
        this(OpenProjectBoardsRuntimeGate.disabled());
    }

    public OpenProjectBoardsRepository(OpenProjectBoardsRuntimeGate runtimeGate) {
        this.runtimeGate = runtimeGate;
    }

    @Override
    public BoardProviderCapabilities capabilities() {
        return new BoardProviderCapabilities(
                ProviderKind.OPEN_PROJECT,
                false,
                EnumSet.of(
                        BoardCapability.COMMENTS,
                        BoardCapability.ATTACHMENTS,
                        BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                        BoardCapability.INCREMENTAL_SYNC,
                        BoardCapability.CUSTOM_FIELDS,
                        BoardCapability.ACCESSIBLE_NON_DRAG_MOVES),
                EnumSet.of(
                        BoardCapability.WEBHOOK_EVENTS,
                        BoardCapability.CHECKLISTS),
                "OpenProject is the disabled, read-only-first Boards provider seam; Vikunja and Deck are comparison/fallback paths only.");
    }

    @Override public BoardPage<WeaveProject> listProjects(BoardQuery query) { throw readSyncDisabled("list-projects"); }
    @Override public BoardPage<Board> listBoards(String projectId, BoardQuery query) { throw readSyncDisabled("list-boards"); }
    @Override public Optional<Board> findBoard(String boardId) { throw readSyncDisabled("find-board"); }
    @Override public BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query) { throw readSyncDisabled("list-columns"); }
    @Override public BoardPage<TaskItem> listTasks(String boardId, TaskQuery query) { throw readSyncDisabled("list-tasks"); }
    @Override public BoardPage<Label> listLabels(String boardId, BoardQuery query) { throw readSyncDisabled("list-labels"); }
    @Override public BoardPage<TaskComment> listComments(String taskId, BoardQuery query) { throw readSyncDisabled("list-comments"); }
    @Override public BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query) { throw readSyncDisabled("list-attachments"); }
    @Override public TaskItem createTask(CreateTaskCommand command) { throw writeDisabled("create-task"); }
    @Override public TaskItem moveTask(MoveTaskCommand command) { throw writeDisabled("move-task"); }
    @Override public TaskItem completeTask(String taskId) { throw writeDisabled("complete-task"); }

    private BoardsException readSyncDisabled(String operation) {
        runtimeGate.requireReadSyncAllowed(operation);
        return new BoardsException(
                BoardsErrorCode.PROVIDER_UNAVAILABLE,
                "OpenProject Boards/Tasks read-sync HTTP client is not implemented until a promotion spec enables authenticated backend routes.");
    }

    private BoardsException writeDisabled(String operation) {
        runtimeGate.requireWriteAllowed(operation);
        return new BoardsException(
                BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                "OpenProject Boards/Tasks writes are not implemented for the read-sync-first adapter seam.");
    }
}
