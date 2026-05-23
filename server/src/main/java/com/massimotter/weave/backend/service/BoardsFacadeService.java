package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsPreviewGuard;
import com.massimotter.weave.backend.boards.port.BoardsRepository;
import com.massimotter.weave.backend.boards.port.CreateTaskCommand;
import com.massimotter.weave.backend.boards.port.MoveTaskCommand;
import com.massimotter.weave.backend.boards.port.TaskQuery;
import com.massimotter.weave.backend.boards.support.BoardsErrorCode;
import com.massimotter.weave.backend.boards.support.BoardsException;
import com.massimotter.weave.backend.config.ContextAuthorizationProperties;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationPort;
import com.massimotter.weave.backend.context.authz.ContextAuthorizationRequest;
import com.massimotter.weave.backend.context.authz.ContextPermission;
import com.massimotter.weave.backend.exception.ApiErrorException;
import com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsPreviewResponse;
import com.massimotter.weave.backend.model.boards.BoardsSyncMetadataResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class BoardsFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final BoardsPreviewGuard previewGuard;
    private final BoardsRepository boardsRepository;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;

    public BoardsFacadeService(
            BoardsPreviewGuard previewGuard,
            BoardsRepository boardsRepository,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties) {
        this.previewGuard = previewGuard;
        this.boardsRepository = boardsRepository;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
    }

    public BoardsPreviewResponse preview(Jwt jwt) {
        requireEnabled();
        requireContextPermission(jwt, ContextPermission.VIEW);
        try {
            var capabilities = boardsRepository.capabilities();
            var nextCursors = new LinkedHashMap<String, String>();

            var projectsPage = boardsRepository.listProjects(BoardQuery.firstPage());
            addCursor(nextCursors, "projects", projectsPage.nextCursor());
            var projects = projectsPage.items();

            var boards = new ArrayList<Board>();
            for (var project : projects) {
                var boardsPage = boardsRepository.listBoards(project.id(), BoardQuery.firstPage());
                boards.addAll(boardsPage.items());
                addCursor(nextCursors, "boards:" + project.id(), boardsPage.nextCursor());
            }

            var tasks = new ArrayList<TaskItem>();
            for (var board : boards) {
                var tasksPage = boardsRepository.listTasks(board.id(), TaskQuery.all());
                tasks.addAll(tasksPage.items());
                addCursor(nextCursors, "tasks:" + board.id(), tasksPage.nextCursor());
            }

            String source = sourceFor(capabilities.provider());
            return new BoardsPreviewResponse(
                    true,
                    "active-feature-gated-preview",
                    source,
                    capabilities,
                    syncMetadata(capabilities.provider(), source, nextCursors, projects, boards, tasks),
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
        PrincipalContext principalContext = principalContext(jwt);
        String contextId = claimOrDefault(jwt, "weave_context_id", "context_id", DEFAULT_CONTEXT_ID);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principalContext.tenantId(),
                contextId,
                principalContext.principalRef(),
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

    private PrincipalContext principalContext(Jwt jwt) {
        if (jwt == null) {
            throw invalidAuthentication("JWT is missing");
        }
        String tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantClaim());
        if (tenantId == null) {
            tenantId = jwtClaim(jwt, contextAuthorizationProperties.tenantFallbackClaim());
        }
        if (tenantId == null) {
            throw invalidAuthentication("tenant claim is missing");
        }
        String configuredClaim = jwtClaim(jwt, contextAuthorizationProperties.principalClaim());
        String principalRef = contextAuthorizationProperties.principalRef(configuredClaim != null ? configuredClaim : jwt.getSubject());
        if (principalRef == null) {
            throw invalidAuthentication("principal claim is missing");
        }
        return new PrincipalContext(tenantId, principalRef);
    }

    private String claimOrDefault(Jwt jwt, String primaryClaim, String fallbackClaim, String defaultValue) {
        String primary = jwtClaim(jwt, primaryClaim);
        if (primary != null) {
            return primary;
        }
        String fallback = jwtClaim(jwt, fallbackClaim);
        if (fallback != null) {
            return fallback;
        }
        return defaultValue;
    }

    private String jwtClaim(Jwt jwt, String claimName) {
        if (jwt == null || claimName == null || claimName.isBlank()) {
            return null;
        }
        String value = jwt.getClaimAsString(claimName);
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private ApiErrorException invalidAuthentication(String reason) {
        return apiError(new BoardsException(
                BoardsErrorCode.UNAUTHORIZED,
                "Boards access requires an authenticated principal.",
                Map.of("reason", reason)));
    }

    private record PrincipalContext(String tenantId, String principalRef) {
    }

    private void requireEnabled() {
        try {
            previewGuard.requireEnabled();
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    private String sourceFor(ProviderKind provider) {
        return switch (provider) {
            case OPEN_PROJECT -> "openproject-read-sync-backend-facade";
            case IN_MEMORY -> "local-preview-backend-facade";
            default -> "provider-neutral-preview-backend-facade";
        };
    }

    private BoardsSyncMetadataResponse syncMetadata(
            ProviderKind provider,
            String source,
            Map<String, String> nextCursors,
            List<WeaveProject> projects,
            List<Board> boards,
            List<TaskItem> tasks) {
        return new BoardsSyncMetadataResponse(
                provider.contractName(),
                provider == ProviderKind.OPEN_PROJECT ? "read-only-sync" : source,
                true,
                true,
                true,
                nextCursors,
                lastSyncedAt(projects, boards, tasks));
    }

    private void addCursor(Map<String, String> nextCursors, String key, String cursor) {
        if (cursor != null && !cursor.isBlank()) {
            String normalized = cursor.toLowerCase(java.util.Locale.ROOT);
            if (cursor.length() > 512
                    || normalized.contains("://")
                    || normalized.contains("token")
                    || normalized.contains("secret")
                    || normalized.contains("password")
                    || normalized.contains("apikey")
                    || normalized.contains("api_key")) {
                throw new BoardsException(
                        BoardsErrorCode.VALIDATION,
                        "Boards adapter returned an unsafe pagination cursor and the preview response was blocked.",
                        Map.of("cursorKey", key, "supportSafe", "true"));
            }
            nextCursors.put(key, cursor);
        }
    }

    private Instant lastSyncedAt(
            List<WeaveProject> projects,
            List<Board> boards,
            List<TaskItem> tasks) {
        return java.util.stream.Stream.of(
                        projects.stream().flatMap(project -> project.providerRefs().stream()),
                        boards.stream().flatMap(board -> board.providerRefs().stream()),
                        tasks.stream().flatMap(task -> task.providerRefs().stream()))
                .flatMap(refs -> refs)
                .map(ProviderRef::lastSyncedAt)
                .filter(java.util.Objects::nonNull)
                .max(Instant::compareTo)
                .orElse(null);
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
