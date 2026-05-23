package com.massimotter.weave.backend.boards.deck;

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
 * Disabled Nextcloud Deck bridge adapter contract. Deck is evaluated as a bridge
 * because Nextcloud is already in the Weave stack, but Deck board/stack/card
 * vocabulary must stay behind this backend boundary.
 */
public final class NextcloudDeckBoardsRepository implements BoardsRepository {

    @Override
    public BoardProviderCapabilities capabilities() {
        return new BoardProviderCapabilities(
                ProviderKind.NEXTCLOUD_DECK,
                false,
                EnumSet.of(
                        BoardCapability.COMMENTS,
                        BoardCapability.ATTACHMENTS,
                        BoardCapability.NON_DESTRUCTIVE_ARCHIVE),
                EnumSet.of(
                        BoardCapability.WEBHOOK_EVENTS,
                        BoardCapability.INCREMENTAL_SYNC,
                        BoardCapability.CHECKLISTS,
                        BoardCapability.CUSTOM_FIELDS,
                        BoardCapability.ACCESSIBLE_NON_DRAG_MOVES),
                "Nextcloud Deck is a disabled bridge/import adapter contract, not the Weave product model or a live runtime provider.");
    }

    @Override public BoardPage<WeaveProject> listProjects(BoardQuery query) { throw disabled(); }
    @Override public BoardPage<Board> listBoards(String projectId, BoardQuery query) { throw disabled(); }
    @Override public Optional<Board> findBoard(String boardId) { throw disabled(); }
    @Override public BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query) { throw disabled(); }
    @Override public BoardPage<TaskItem> listTasks(String boardId, TaskQuery query) { throw disabled(); }
    @Override public BoardPage<Label> listLabels(String boardId, BoardQuery query) { throw disabled(); }
    @Override public BoardPage<TaskComment> listComments(String taskId, BoardQuery query) { throw disabled(); }
    @Override public BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query) { throw disabled(); }
    @Override public TaskItem createTask(CreateTaskCommand command) { throw disabled(); }
    @Override public TaskItem moveTask(MoveTaskCommand command) { throw disabled(); }
    @Override public TaskItem completeTask(String taskId) { throw disabled(); }

    private BoardsException disabled() {
        return new BoardsException(
                BoardsErrorCode.PROVIDER_UNAVAILABLE,
                "Nextcloud Deck Boards/Tasks adapter is bridge-only and disabled until a promotion spec enables backend routes.");
    }
}
