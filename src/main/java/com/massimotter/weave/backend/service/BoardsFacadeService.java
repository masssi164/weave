package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsPreviewResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class BoardsFacadeService {

    private static final String DEFAULT_TENANT_ID = "tenant-default";
    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final BoardsPreviewGuard previewGuard;
    private final BoardsRepository boardsRepository;
    private final ContextAuthorizationPort contextAuthorizationPort;

    public BoardsFacadeService(
            BoardsPreviewGuard previewGuard,
            BoardsRepository boardsRepository,
            ContextAuthorizationPort contextAuthorizationPort) {
        this.previewGuard = previewGuard;
        this.boardsRepository = boardsRepository;
        this.contextAuthorizationPort = contextAuthorizationPort;
    }

    public BoardsPreviewResponse preview(Jwt jwt) {
        requireEnabled();
        requireContextPermission(jwt, ContextPermission.VIEW);
        try {
            var projects = boardsRepository.listProjects(BoardQuery.firstPage()).items();
            var boards = projects.stream()
                    .flatMap(project -> boardsRepository.listBoards(project.id(), BoardQuery.firstPage()).items().stream())
                    .toList();
            var tasks = boards.stream()
                    .flatMap(board -> boardsRepository.listTasks(board.id(), TaskQuery.all()).items().stream())
                    .toList();
            return new BoardsPreviewResponse(
                    true,
                    "active-feature-gated-preview",
                    "local-preview-backend-facade",
                    boardsRepository.capabilities(),
                    projects,
                    boards,
                    tasks);
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public TaskItem createTask(Jwt jwt, String boardId, BoardsCreateTaskRequest request) {
        requireEnabled();
        requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            return boardsRepository.createTask(new CreateTaskCommand(
                    boardId,
                    request.columnId(),
                    request.title(),
                    request.description(),
                    request.assigneeRefs(),
                    request.labelRefs(),
                    request.dueAt()));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public TaskItem moveTask(Jwt jwt, String taskId, BoardsMoveTaskRequest request) {
        requireEnabled();
        requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            return boardsRepository.moveTask(new MoveTaskCommand(
                    taskId,
                    request.targetColumnId(),
                    request.targetPosition()));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public TaskItem completeTask(Jwt jwt, String taskId) {
        requireEnabled();
        requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            return boardsRepository.completeTask(taskId);
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    private void requireContextPermission(Jwt jwt, ContextPermission permission) {
        String principalRef = principalRef(jwt);
        String tenantId = claimOrDefault(jwt, "weave_tenant_id", "tenant_id", DEFAULT_TENANT_ID);
        String contextId = claimOrDefault(jwt, "weave_context_id", "context_id", DEFAULT_CONTEXT_ID);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                tenantId,
                contextId,
                principalRef,
                permission));
        if (!decision.allowed()) {
            throw apiError(new BoardsException(
                    BoardsErrorCode.FORBIDDEN,
                    "Boards access is not allowed for this Context/Space.",
                    Map.of(
                            "reason", decision.reason(),
                            "contextId", contextId,
                            "permission", permission.name().toLowerCase())));
        }
    }

    private String principalRef(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw apiError(new BoardsException(
                    BoardsErrorCode.UNAUTHORIZED,
                    "Boards access requires an authenticated principal."));
        }
        return "user:" + jwt.getSubject();
    }

    private String claimOrDefault(Jwt jwt, String primaryClaim, String fallbackClaim, String defaultValue) {
        if (jwt == null) {
            return defaultValue;
        }
        String primary = jwt.getClaimAsString(primaryClaim);
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        String fallback = jwt.getClaimAsString(fallbackClaim);
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return defaultValue;
    }

    private void requireEnabled() {
        try {
            previewGuard.requireEnabled();
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    private ApiErrorException apiError(BoardsException exception) {
        HttpStatus status = switch (exception.code()) {
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            case FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RATE_LIMITED -> HttpStatus.TOO_MANY_REQUESTS;
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case PROVIDER_UNAVAILABLE, OFFLINE, UNSUPPORTED_CAPABILITY -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", "boards");
        details.put("preview", true);
        details.put("releaseStatus", "active-feature-gated-preview");
        details.putAll(exception.details());
        return new ApiErrorException(status, "boards-" + exception.code().contractName(), exception.getMessage(), details);
    }
}
