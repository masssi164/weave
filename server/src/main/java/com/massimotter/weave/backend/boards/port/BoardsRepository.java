package com.massimotter.weave.backend.boards.port;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.Label;
import com.massimotter.weave.backend.boards.domain.TaskAttachment;
import com.massimotter.weave.backend.boards.domain.TaskComment;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.WeaveProject;

import java.util.Optional;

/**
 * Provider-neutral Boards repository contract. Implementations may talk to Vikunja,
 * a future connector, or an in-memory fixture, but callers must not depend on
 * provider-specific vocabulary, IDs, pagination, or errors.
 */
public interface BoardsRepository {

    BoardProviderCapabilities capabilities();

    BoardPage<WeaveProject> listProjects(BoardQuery query);

    BoardPage<Board> listBoards(String projectId, BoardQuery query);

    Optional<Board> findBoard(String boardId);

    BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query);

    BoardPage<TaskItem> listTasks(String boardId, TaskQuery query);

    BoardPage<Label> listLabels(String boardId, BoardQuery query);

    BoardPage<TaskComment> listComments(String taskId, BoardQuery query);

    BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query);

    TaskItem createTask(CreateTaskCommand command);

    TaskItem moveTask(MoveTaskCommand command);

    TaskItem completeTask(String taskId);
}
