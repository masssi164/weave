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
                EnumSet.of(
                        BoardCapability.INCREMENTAL_SYNC),
                EnumSet.of(
                        BoardCapability.COMMENTS,
                        BoardCapability.ATTACHMENTS,
                        BoardCapability.NON_DESTRUCTIVE_ARCHIVE,
                        BoardCapability.WEBHOOK_EVENTS,
                        BoardCapability.CHECKLISTS,
                        BoardCapability.CUSTOM_FIELDS,
                        BoardCapability.ACCESSIBLE_NON_DRAG_MOVES),
                enabled
                        ? "OpenProject is enabled for authenticated read-only Boards sync of projects, statuses, and work packages; comments, attachments, archive, and writes remain gated behind audit/consent promotion."
                        : "OpenProject is the disabled, read-only-first Boards provider seam; Vikunja and Deck are comparison/fallback paths only.");
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

    @Override public TaskItem createTask(CreateTaskCommand command) { throw writeDisabled("create-task"); }
    @Override public TaskItem moveTask(MoveTaskCommand command) { throw writeDisabled("move-task"); }
    @Override public TaskItem completeTask(String taskId) { throw writeDisabled("complete-task"); }

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

    private BoardsException writeDisabled(String operation) {
        runtimeGate.requireWriteAllowed(operation);
        return new BoardsException(
                BoardsErrorCode.UNSUPPORTED_CAPABILITY,
                "OpenProject Boards/Tasks writes are not implemented for the read-sync-first adapter seam.");
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
