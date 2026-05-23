package com.massimotter.weave.backend.boards.local;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.BoardCapability;
import com.massimotter.weave.backend.boards.domain.BoardColumn;
import com.massimotter.weave.backend.boards.domain.BoardProviderCapabilities;
import com.massimotter.weave.backend.boards.domain.ColumnSemanticStatus;
import com.massimotter.weave.backend.boards.domain.Label;
import com.massimotter.weave.backend.boards.domain.ProjectVisibility;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskAttachment;
import com.massimotter.weave.backend.boards.domain.TaskComment;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.TaskPriority;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.port.BoardPage;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local Boards/Tasks runtime slice for the provider-neutral workspace facade.
 * It stores dogfood workspace task data in memory and never contacts or exposes
 * upstream provider systems.
 */
public class LocalWorkspaceBoardsRepository implements BoardsRepository {

    private static final String PROJECT_ID = "local-project-1";
    private static final String BOARD_ID = "local-board-1";
    private static final String TODO_COLUMN = "local-column-todo";
    private static final String ACTIVE_COLUMN = "local-column-active";
    private static final String BLOCKED_COLUMN = "local-column-blocked";
    private static final String DONE_COLUMN = "local-column-done";

    private final Instant seededAt;
    private final Map<String, TaskItem> tasks = new ConcurrentHashMap<>();

    public LocalWorkspaceBoardsRepository() {
        this(Instant.parse("2026-05-14T18:00:00Z"));
    }

    LocalWorkspaceBoardsRepository(Instant seededAt) {
        this.seededAt = seededAt;
        seed();
    }

    @Override
    public BoardProviderCapabilities capabilities() {
        return new BoardProviderCapabilities(
                ProviderKind.IN_MEMORY,
                true,
                Set.of(
                        BoardCapability.ACCESSIBLE_NON_DRAG_MOVES,
                        BoardCapability.STATUS_UPDATES,
                        BoardCapability.DECISION_LINKS),
                Set.of(
                        BoardCapability.COMMENTS,
                        BoardCapability.ATTACHMENTS,
                        BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                        BoardCapability.WEBHOOK_EVENTS,
                        BoardCapability.INCREMENTAL_SYNC,
                        BoardCapability.CHECKLISTS,
                        BoardCapability.CUSTOM_FIELDS),
                "Local workspace facade: create, move, status-update, complete, and decision-link user tasks with audit-ready backend actions; no provider secrets are exposed.");
    }

    @Override
    public BoardPage<WeaveProject> listProjects(BoardQuery query) {
        return BoardPage.singlePage(List.of(new WeaveProject(
                PROJECT_ID,
                "Weave workspace",
                ProjectVisibility.WORKSPACE,
                List.of("workspace:members"),
                List.of())));
    }

    @Override
    public BoardPage<Board> listBoards(String projectId, BoardQuery query) {
        ensureProject(projectId);
        return BoardPage.singlePage(List.of(board()));
    }

    @Override
    public java.util.Optional<Board> findBoard(String boardId) {
        return BOARD_ID.equals(boardId) ? java.util.Optional.of(board()) : java.util.Optional.empty();
    }

    @Override
    public BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query) {
        ensureBoard(boardId);
        return BoardPage.singlePage(columns());
    }

    @Override
    public BoardPage<TaskItem> listTasks(String boardId, TaskQuery query) {
        ensureBoard(boardId);
        return BoardPage.singlePage(sortedTasks().stream()
                .filter(task -> query == null || query.columnId() == null || query.columnId().equals(task.columnId()))
                .toList());
    }

    @Override
    public BoardPage<Label> listLabels(String boardId, BoardQuery query) {
        ensureBoard(boardId);
        return BoardPage.singlePage(List.of());
    }

    @Override
    public BoardPage<TaskComment> listComments(String taskId, BoardQuery query) {
        ensureTask(taskId);
        return BoardPage.singlePage(List.of());
    }

    @Override
    public BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query) {
        ensureTask(taskId);
        return BoardPage.singlePage(List.of());
    }

    @Override
    public TaskItem createTask(CreateTaskCommand command) {
        ensureBoard(command.boardId());
        ensureColumn(command.columnId());
        Instant now = Instant.now();
        String id = "local-task-" + UUID.randomUUID();
        int nextPosition = (int) tasks.values().stream()
                .filter(task -> command.columnId().equals(task.columnId()))
                .count();
        TaskItem task = new TaskItem(
                id,
                command.boardId(),
                command.columnId(),
                command.title(),
                command.description(),
                TaskStatus.OPEN,
                nextPosition,
                command.assigneeRefs(),
                command.labelRefs(),
                List.of(),
                TaskPriority.NORMAL,
                null,
                command.dueAt(),
                null,
                now,
                List.of());
        tasks.put(id, task);
        return task;
    }

    @Override
    public TaskItem moveTask(MoveTaskCommand command) {
        ensureColumn(command.targetColumnId());
        TaskItem current = ensureTask(command.taskId());
        TaskItem moved = current.moveTo(command.targetColumnId(), command.targetPosition(), Instant.now());
        tasks.put(command.taskId(), moved);
        return moved;
    }

    @Override
    public TaskItem completeTask(String taskId) {
        TaskItem current = ensureTask(taskId);
        TaskItem completed = current.withStatus(TaskStatus.COMPLETED, DONE_COLUMN, Instant.now());
        tasks.put(taskId, completed);
        return completed;
    }

    @Override
    public TaskItem updateTaskStatus(String taskId, TaskStatus status, String targetColumnId) {
        TaskItem current = ensureTask(taskId);
        String columnId = targetColumnId == null || targetColumnId.isBlank()
                ? columnForStatus(status)
                : targetColumnId;
        ensureColumn(columnId);
        TaskItem updated = current.withStatus(status, columnId, Instant.now());
        tasks.put(taskId, updated);
        return updated;
    }

    @Override
    public TaskItem linkDecision(String taskId, String decisionRef) {
        TaskItem current = ensureTask(taskId);
        TaskItem updated = current.withDecisionRef(decisionRef, Instant.now());
        tasks.put(taskId, updated);
        return updated;
    }

    private Board board() {
        return new Board(
                BOARD_ID,
                PROJECT_ID,
                "Launch readiness board",
                "Provider-neutral workspace board fed by the backend facade, not by a static Flutter-only fixture.",
                columns(),
                false,
                List.of());
    }

    private List<BoardColumn> columns() {
        return List.of(
                new BoardColumn(TODO_COLUMN, BOARD_ID, "To do", 0, ColumnSemanticStatus.NOT_STARTED, null, List.of()),
                new BoardColumn(ACTIVE_COLUMN, BOARD_ID, "In progress", 1, ColumnSemanticStatus.IN_PROGRESS, 4, List.of()),
                new BoardColumn(BLOCKED_COLUMN, BOARD_ID, "Blocked", 2, ColumnSemanticStatus.BLOCKED, null, List.of()),
                new BoardColumn(DONE_COLUMN, BOARD_ID, "Done", 3, ColumnSemanticStatus.DONE, null, List.of()));
    }

    private String columnForStatus(TaskStatus status) {
        return switch (status) {
            case OPEN -> ACTIVE_COLUMN;
            case BLOCKED -> BLOCKED_COLUMN;
            case COMPLETED -> DONE_COLUMN;
            case ARCHIVED -> DONE_COLUMN;
        };
    }

    private List<TaskItem> sortedTasks() {
        Map<String, Integer> columnOrder = new LinkedHashMap<>();
        List<BoardColumn> columns = columns();
        for (int i = 0; i < columns.size(); i++) {
            columnOrder.put(columns.get(i).id(), i);
        }
        return tasks.values().stream()
                .sorted(Comparator
                        .comparing((TaskItem task) -> columnOrder.getOrDefault(task.columnId(), 99))
                        .thenComparing(TaskItem::position)
                        .thenComparing(TaskItem::title))
                .toList();
    }

    private void seed() {
        List<TaskItem> seedTasks = new ArrayList<>();
        seedTasks.add(task("local-task-contract", TODO_COLUMN, "Review board API contract", "Keep routes hidden and provider-neutral before enabling a live adapter.", 0, TaskStatus.OPEN));
        seedTasks.add(task("local-task-a11y", ACTIVE_COLUMN, "Validate non-drag movement", "Keyboard and screen-reader users need move and complete actions without pointer drag.", 0, TaskStatus.OPEN));
        seedTasks.add(task("local-task-provider", BLOCKED_COLUMN, "Promote provider adapter gates", "OpenProject is the preferred read-sync seam; Vikunja and Deck remain comparison paths behind the Weave model.", 0, TaskStatus.BLOCKED));
        seedTasks.add(task("local-task-events", DONE_COLUMN, "Normalize task events", "Event envelopes redact provider details before later notifications or audits consume them.", 0, TaskStatus.COMPLETED));
        seedTasks.forEach(task -> tasks.put(task.id(), task));
    }

    private TaskItem task(String id, String columnId, String title, String description, int position, TaskStatus status) {
        return new TaskItem(
                id,
                BOARD_ID,
                columnId,
                title,
                description,
                status,
                position,
                List.of("workspace:member"),
                List.of(),
                List.of(),
                TaskPriority.NORMAL,
                null,
                null,
                status == TaskStatus.COMPLETED ? seededAt : null,
                seededAt,
                List.of(new ProviderRef(ProviderKind.IN_MEMORY, id, null, null, null, seededAt)));
    }

    private void ensureProject(String projectId) {
        if (!PROJECT_ID.equals(projectId)) {
            throw notFound("project");
        }
    }

    private void ensureBoard(String boardId) {
        if (!BOARD_ID.equals(boardId)) {
            throw notFound("board");
        }
    }

    private void ensureColumn(String columnId) {
        boolean exists = columns().stream().anyMatch(column -> column.id().equals(columnId));
        if (!exists) {
            throw notFound("column");
        }
    }

    private TaskItem ensureTask(String taskId) {
        TaskItem task = tasks.get(taskId);
        if (task == null) {
            throw notFound("task");
        }
        return task;
    }

    private BoardsException notFound(String resource) {
        return new BoardsException(
                BoardsErrorCode.NOT_FOUND,
                "Boards workspace " + resource + " was not found.",
                Map.of("resource", resource));
    }
}
