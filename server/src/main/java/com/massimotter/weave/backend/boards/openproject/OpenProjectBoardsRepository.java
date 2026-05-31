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
import java.net.URI;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;

/**
 * OpenProject-first read-sync adapter contract. OpenProject is the preferred
 * first provider-backed validation target because its API can map
 * projects/statuses/work packages into Weave Boards without making provider
 * vocabulary the product model. HTTP read-sync is available only when the
 * fail-closed runtime gates and backend-held provider credentials are present;
 * provider writes remain disabled until a later audit/consent promotion spec.
 */
public final class OpenProjectBoardsRepository implements BoardsRepository {

    private final OpenProjectBoardsRuntimeGate runtimeGate;
    private final OpenProjectBoardsClient client;
    private final OpenProjectBoardsMapper mapper;

    public OpenProjectBoardsRepository() {
        this(OpenProjectBoardsRuntimeGate.disabled());
    }

    public OpenProjectBoardsRepository(OpenProjectBoardsRuntimeGate runtimeGate) {
        this(runtimeGate, null, "", RestClient.builder());
    }

    public OpenProjectBoardsRepository(
            OpenProjectBoardsRuntimeGate runtimeGate,
            URI baseUrl,
            String apiToken,
            RestClient.Builder restClientBuilder) {
        this.runtimeGate = runtimeGate;
        this.client = new OpenProjectBoardsClient(baseUrl, apiToken, restClientBuilder);
        this.mapper = new OpenProjectBoardsMapper();
    }

    @Override
    public BoardProviderCapabilities capabilities() {
        boolean enabled = runtimeGate.readSyncConfigured() && client.isConfigured();
        return new BoardProviderCapabilities(
                ProviderKind.OPEN_PROJECT,
                enabled,
                runtimeGate.writeConfigured()
                        ? EnumSet.of(
                                BoardCapability.INCREMENTAL_SYNC,
                                BoardCapability.STATUS_UPDATES,
                                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES)
                        : EnumSet.of(
                                BoardCapability.INCREMENTAL_SYNC),
                runtimeGate.writeConfigured()
                        ? EnumSet.of(
                                BoardCapability.COMMENTS,
                                BoardCapability.ATTACHMENTS,
                                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                                BoardCapability.WEBHOOK_EVENTS,
                                BoardCapability.CHECKLISTS,
                                BoardCapability.CUSTOM_FIELDS,
                                BoardCapability.DECISION_LINKS)
                        : EnumSet.of(
                                BoardCapability.COMMENTS,
                                BoardCapability.ATTACHMENTS,
                                BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                                BoardCapability.STATUS_UPDATES,
                                BoardCapability.DECISION_LINKS,
                                BoardCapability.WEBHOOK_EVENTS,
                                BoardCapability.CHECKLISTS,
                                BoardCapability.CUSTOM_FIELDS,
                                BoardCapability.ACCESSIBLE_NON_DRAG_MOVES),
                enabled
                        ? "OpenProject is enabled for authenticated Boards workspace sync of projects, statuses, and work packages; status moves use OpenProject lockVersion optimistic locking when provider writes are promoted; comments, attachments, archive, and decision links remain unsupported."
                        : "OpenProject is the disabled Boards workspace-sync provider seam; Vikunja and Deck are comparison/fallback paths only.");
    }

    @Override
    public BoardPage<WeaveProject> listProjects(BoardQuery query) {
        requireReadSync("list-projects");
        BoardPage<OpenProjectProjectSnapshot> page = client.listProjects(query);
        return new BoardPage<>(page.items().stream().map(mapper::toProject).toList(), page.nextCursor());
    }

    @Override
    public BoardPage<Board> listBoards(String projectId, BoardQuery query) {
        requireReadSync("list-boards");
        OpenProjectProjectSnapshot project = project(projectId);
        List<BoardColumn> columns = listColumnsForProject(project.id(), query);
        return BoardPage.singlePage(List.of(mapper.toBoard(project, columns)));
    }

    @Override
    public Optional<Board> findBoard(String boardId) {
        requireReadSync("find-board");
        long projectId = parseWeaveId(boardId, "board", "find-board");
        return client.findProject(projectId)
                .map(project -> mapper.toBoard(project, listColumnsForProject(project.id(), BoardQuery.firstPage())));
    }

    @Override
    public BoardPage<BoardColumn> listColumns(String boardId, BoardQuery query) {
        requireReadSync("list-columns");
        long projectId = parseWeaveId(boardId, "board", "list-columns");
        return BoardPage.singlePage(listColumnsForProject(projectId, query));
    }

    @Override
    public BoardPage<TaskItem> listTasks(String boardId, TaskQuery query) {
        requireReadSync("list-tasks");
        long projectId = parseWeaveId(boardId, "board", "list-tasks");
        Map<Long, OpenProjectStatusSnapshot> statusesById = client.listStatuses(BoardQuery.firstPage()).items().stream()
                .collect(Collectors.toMap(OpenProjectStatusSnapshot::id, Function.identity(), (first, ignored) -> first));
        BoardPage<OpenProjectWorkPackageSnapshot> page = client.listWorkPackages(projectId, query);
        return new BoardPage<>(page.items().stream()
                .map(task -> mapper.toTask(task, statusesById))
                .toList(), page.nextCursor());
    }

    @Override
    public BoardPage<Label> listLabels(String boardId, BoardQuery query) {
        requireReadSync("list-labels");
        return BoardPage.singlePage(List.of());
    }

    @Override
    public BoardPage<TaskComment> listComments(String taskId, BoardQuery query) {
        throw unsupportedUntilPromotion("list-comments", "comments");
    }

    @Override
    public BoardPage<TaskAttachment> listAttachments(String taskId, BoardQuery query) {
        throw unsupportedUntilPromotion("list-attachments", "attachments");
    }

    @Override
    public TaskItem createTask(CreateTaskCommand command) {
        throw unsupportedWrite("create-task", "create_task", "OpenProject task creation requires type/form-schema mapping that is not yet release-safe.");
    }

    @Override
    public TaskItem moveTask(MoveTaskCommand command) {
        runtimeGate.requireWriteAllowed("move-task");
        long taskId = parseWeaveId(command.taskId(), "work-package", "move-task");
        long statusId = parseWeaveId(command.targetColumnId(), "status", "move-task");
        OpenProjectStatusSnapshot targetStatus = status(statusId, "move-task");
        TaskStatus providerNeutralStatus = mapper.toTaskStatus(targetStatus);
        requireSupportedWriteStatus(providerNeutralStatus, "move-task");
        OpenProjectWorkPackageSnapshot current = workPackage(taskId, "move-task");
        OpenProjectWorkPackageSnapshot updated = client.updateWorkPackage(
                taskId,
                current.lockVersion(),
                statusId,
                command.targetPosition(),
                "move-task");
        return mapper.toTask(updated, providerNeutralStatus);
    }

    @Override
    public TaskItem completeTask(String taskId) {
        runtimeGate.requireWriteAllowed("complete-task");
        long workPackageId = parseWeaveId(taskId, "work-package", "complete-task");
        OpenProjectStatusSnapshot done = firstClosedStatus("complete-task");
        OpenProjectWorkPackageSnapshot current = workPackage(workPackageId, "complete-task");
        OpenProjectWorkPackageSnapshot updated = client.updateWorkPackage(
                workPackageId,
                current.lockVersion(),
                done.id(),
                null,
                "complete-task");
        return mapper.toTask(updated, statusesById());
    }

    @Override
    public TaskItem updateTaskStatus(String taskId, TaskStatus status, String targetColumnId) {
        runtimeGate.requireWriteAllowed("update-task-status");
        long workPackageId = parseWeaveId(taskId, "work-package", "update-task-status");
        requireSupportedWriteStatus(status, "update-task-status");
        long statusId = targetStatusId(status, targetColumnId, "update-task-status");
        if (targetColumnId != null && !targetColumnId.isBlank()) {
            requireSupportedWriteStatus(mapper.toTaskStatus(status(statusId, "update-task-status")), "update-task-status");
        }
        OpenProjectWorkPackageSnapshot current = workPackage(workPackageId, "update-task-status");
        OpenProjectWorkPackageSnapshot updated = client.updateWorkPackage(
                workPackageId,
                current.lockVersion(),
                statusId,
                null,
                "update-task-status");
        return mapper.toTask(updated, status);
    }

    @Override
    public TaskItem linkDecision(String taskId, String decisionRef) {
        throw unsupportedWrite("link-decision", "decision_links", "OpenProject has no release-safe Weave decision-link field mapping yet.");
    }

    private void requireReadSync(String operation) {
        runtimeGate.requireReadSyncAllowed(operation);
    }

    private OpenProjectProjectSnapshot project(String projectId) {
        long id = parseWeaveId(projectId, "project", "list-boards");
        return client.findProject(id).orElseThrow(() -> new BoardsException(
                BoardsErrorCode.NOT_FOUND,
                "OpenProject project was not found for the Weave Boards read-sync request.",
                Map.of("provider", "openproject", "operation", "list-boards", "supportSafe", "true")));
    }

    private List<BoardColumn> listColumnsForProject(long projectId, BoardQuery query) {
        return client.listStatuses(query == null ? BoardQuery.firstPage() : query).items().stream()
                .map(status -> mapper.toColumn(projectId, status))
                .toList();
    }

    private long parseWeaveId(String weaveId, String expectedType, String operation) {
        String prefix = "openproject:" + expectedType + ":";
        if (weaveId == null || !weaveId.startsWith(prefix)) {
            throw new BoardsException(
                    BoardsErrorCode.VALIDATION,
                    "OpenProject Boards read-sync received an invalid provider-neutral Weave identifier.",
                    Map.of("provider", "openproject", "operation", operation, "supportSafe", "true"));
        }
        try {
            return Long.parseLong(weaveId.substring(prefix.length()));
        } catch (NumberFormatException exception) {
            throw new BoardsException(
                    BoardsErrorCode.VALIDATION,
                    "OpenProject Boards read-sync received an invalid provider-neutral Weave identifier.",
                    Map.of("provider", "openproject", "operation", operation, "supportSafe", "true"));
        }
    }

    private OpenProjectWorkPackageSnapshot workPackage(long workPackageId, String operation) {
        return client.findWorkPackageForWrite(workPackageId, operation).orElseThrow(() -> new BoardsException(
                BoardsErrorCode.NOT_FOUND,
                "OpenProject work package was not found for the Weave Boards write request.",
                Map.of("provider", "openproject", "operation", operation, "supportSafe", "true")));
    }


    private OpenProjectStatusSnapshot status(long statusId, String operation) {
        OpenProjectStatusSnapshot status = statusesById().get(statusId);
        if (status == null) {
            throw new BoardsException(
                    BoardsErrorCode.NOT_FOUND,
                    "OpenProject status was not found for the Weave Boards write request.",
                    Map.of("provider", "openproject", "operation", operation, "mode", "write", "supportSafe", "true"));
        }
        return status;
    }

    private void requireSupportedWriteStatus(TaskStatus status, String operation) {
        if (status == TaskStatus.ARCHIVED) {
            throw unsupportedWrite(operation, "non_destructive_archive", "OpenProject Boards archive-like provider statuses remain fail-closed until non-destructive archive evidence is implemented.");
        }
    }

    private Map<Long, OpenProjectStatusSnapshot> statusesById() {
        return client.listStatuses(BoardQuery.firstPage()).items().stream()
                .collect(Collectors.toMap(OpenProjectStatusSnapshot::id, Function.identity(), (first, ignored) -> first));
    }

    private OpenProjectStatusSnapshot firstClosedStatus(String operation) {
        return client.listStatuses(BoardQuery.firstPage()).items().stream()
                .filter(OpenProjectStatusSnapshot::closed)
                .findFirst()
                .orElseThrow(() -> new BoardsException(
                        BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                        "OpenProject Boards complete-task requires a closed provider status mapping.",
                        Map.of("provider", "openproject", "operation", operation, "capability", "status_updates", "supportSafe", "true")));
    }

    private long targetStatusId(TaskStatus status, String targetColumnId, String operation) {
        if (targetColumnId != null && !targetColumnId.isBlank()) {
            return parseWeaveId(targetColumnId, "status", operation);
        }
        if (status == TaskStatus.COMPLETED) {
            return firstClosedStatus(operation).id();
        }
        throw new BoardsException(
                BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                "OpenProject Boards status update requires a target column unless completing a task.",
                Map.of("provider", "openproject", "operation", operation, "capability", "status_updates", "supportSafe", "true"));
    }

    private BoardsException unsupportedWrite(String operation, String capability, String reason) {
        return new BoardsException(
                BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                reason,
                Map.of(
                        "provider", "openproject",
                        "operation", operation,
                        "capability", capability,
                        "mode", "write",
                        "supportSafe", "true"));
    }

    private BoardsException unsupportedUntilPromotion(String operation, String capability) {
        return new BoardsException(
                BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                "OpenProject Boards " + capability + " remain disabled until audit, consent, and support-safe provider promotion are explicitly implemented.",
                Map.of(
                        "provider", "openproject",
                        "operation", operation,
                        "capability", capability,
                        "mode", "read_sync",
                        "supportSafe", "true"));
    }
}
