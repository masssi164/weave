package com.massimotter.weave.backend.service;

import com.massimotter.weave.backend.audit.AuditAction;
import com.massimotter.weave.backend.audit.AuditEvent;
import com.massimotter.weave.backend.audit.AuditEventPublisher;
import com.massimotter.weave.backend.audit.AuditRedactionLevel;
import com.massimotter.weave.backend.audit.AuditWriteGate;
import com.massimotter.weave.backend.audit.InMemoryAuditEventPublisher;
import com.massimotter.weave.backend.boards.domain.Board;
import com.massimotter.weave.backend.boards.domain.ProviderKind;
import com.massimotter.weave.backend.boards.domain.ProviderRef;
import com.massimotter.weave.backend.boards.domain.TaskItem;
import com.massimotter.weave.backend.boards.domain.TaskStatus;
import com.massimotter.weave.backend.boards.domain.WeaveProject;
import com.massimotter.weave.backend.boards.port.BoardQuery;
import com.massimotter.weave.backend.boards.port.BoardsRuntimeGuard;
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
import com.massimotter.weave.backend.model.boards.BoardsBoardResponse;
import com.massimotter.weave.backend.model.boards.BoardsColumnResponse;
import com.massimotter.weave.backend.model.boards.BoardsCreateTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsLinkDecisionRequest;
import com.massimotter.weave.backend.model.boards.BoardsMoveTaskRequest;
import com.massimotter.weave.backend.model.boards.BoardsProjectResponse;
import com.massimotter.weave.backend.model.boards.BoardsSyncMetadataResponse;
import com.massimotter.weave.backend.model.boards.BoardsTaskResponse;
import com.massimotter.weave.backend.model.boards.BoardsUpdateTaskStatusRequest;
import com.massimotter.weave.backend.model.boards.BoardsWorkspaceResponse;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
public class BoardsFacadeService {

    private static final String DEFAULT_CONTEXT_ID = "workspace-default";

    private final BoardsRuntimeGuard runtimeGuard;
    private final BoardsRepository boardsRepository;
    private final ContextAuthorizationPort contextAuthorizationPort;
    private final ContextAuthorizationProperties contextAuthorizationProperties;
    private final WorkspaceCapabilityService workspaceCapabilityService;
    private final AuditEventPublisher auditEventPublisher;

    public BoardsFacadeService(
            BoardsRuntimeGuard runtimeGuard,
            BoardsRepository boardsRepository,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService) {
        this(runtimeGuard, boardsRepository, contextAuthorizationPort, contextAuthorizationProperties, workspaceCapabilityService, new InMemoryAuditEventPublisher());
    }

    @Autowired
    public BoardsFacadeService(
            BoardsRuntimeGuard runtimeGuard,
            BoardsRepository boardsRepository,
            ContextAuthorizationPort contextAuthorizationPort,
            ContextAuthorizationProperties contextAuthorizationProperties,
            WorkspaceCapabilityService workspaceCapabilityService,
            AuditEventPublisher auditEventPublisher) {
        this.runtimeGuard = runtimeGuard;
        this.boardsRepository = boardsRepository;
        this.contextAuthorizationPort = contextAuthorizationPort;
        this.contextAuthorizationProperties = contextAuthorizationProperties;
        this.workspaceCapabilityService = workspaceCapabilityService;
        this.auditEventPublisher = auditEventPublisher;
    }

    public BoardsWorkspaceResponse workspace(Jwt jwt) {
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
            return new BoardsWorkspaceResponse(
                    true,
                    "active-dogfood-production",
                    source,
                    syncMetadata(source, nextCursors, projects, boards, tasks),
                    projects.stream().map(this::projectResponse).toList(),
                    boards.stream().map(this::boardResponse).toList(),
                    tasks.stream().map(this::taskResponse).toList());
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public BoardsTaskResponse createTask(Jwt jwt, String boardId, BoardsCreateTaskRequest request) {
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            publishTaskWriteAudit(principal, AuditAction.BOARD_TASK_CREATED, "create_task:" + boardId, Map.of(
                    "command", "create_task",
                    "boardId", boardId,
                    "columnId", request.columnId(),
                    "mappingRef", mappingRef("task", boardId + ":" + request.columnId() + ":" + request.title())));
            return taskResponse(boardsRepository.createTask(new CreateTaskCommand(
                    boardId,
                    request.columnId(),
                    request.title(),
                    request.description(),
                    request.assigneeRefs(),
                    request.labelRefs(),
                    request.dueAt())));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public BoardsTaskResponse moveTask(Jwt jwt, String taskId, BoardsMoveTaskRequest request) {
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            publishTaskWriteAudit(principal, AuditAction.BOARD_TASK_MOVED, "move_task:" + taskId, Map.of(
                    "command", "move_task",
                    "taskId", taskId,
                    "targetColumnId", request.targetColumnId(),
                    "targetPosition", request.targetPosition(),
                    "mappingRef", mappingRef("task", taskId)));
            return taskResponse(boardsRepository.moveTask(new MoveTaskCommand(
                    taskId,
                    request.targetColumnId(),
                    request.targetPosition())));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public BoardsTaskResponse completeTask(Jwt jwt, String taskId) {
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            publishTaskWriteAudit(principal, AuditAction.BOARD_TASK_COMPLETED, "complete_task:" + taskId, Map.of(
                    "command", "complete_task",
                    "taskId", taskId,
                    "mappingRef", mappingRef("task", taskId)));
            return taskResponse(boardsRepository.completeTask(taskId));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public BoardsTaskResponse updateTaskStatus(Jwt jwt, String taskId, BoardsUpdateTaskStatusRequest request) {
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        TaskStatus status = parseStatus(request.status());
        try {
            publishTaskWriteAudit(principal, AuditAction.TASK_STATUS_UPDATED, "update_task_status:" + taskId, Map.of(
                    "command", "update_task_status",
                    "taskId", taskId,
                    "status", status.contractName(),
                    "targetColumnId", request.targetColumnId() == null ? "" : request.targetColumnId(),
                    "mappingRef", mappingRef("task", taskId)));
            return taskResponse(boardsRepository.updateTaskStatus(taskId, status, request.targetColumnId()));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    public BoardsTaskResponse linkDecision(Jwt jwt, String taskId, BoardsLinkDecisionRequest request) {
        PrincipalContext principal = requireContextPermission(jwt, ContextPermission.EDIT);
        try {
            publishTaskWriteAudit(principal, AuditAction.TASK_DECISION_LINKED, "link_decision:" + taskId, Map.of(
                    "command", "link_decision",
                    "taskId", taskId,
                    "decisionRef", request.decisionRef() == null ? "" : request.decisionRef(),
                    "mappingRef", mappingRef("task", taskId)));
            return taskResponse(boardsRepository.linkDecision(taskId, request.decisionRef()));
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    private PrincipalContext requireContextPermission(Jwt jwt, ContextPermission permission) {
        requireEnabled();
        workspaceCapabilityService.requireCapability(jwt, capabilityFor(permission), "boards", operationFor(permission));
        PrincipalContext principalContext = principalContext(jwt);
        var decision = contextAuthorizationPort.check(new ContextAuthorizationRequest(
                principalContext.tenantId(),
                principalContext.contextId(),
                principalContext.principalRef(),
                permission));
        if (!decision.allowed()) {
            throw apiError(new BoardsException(
                    BoardsErrorCode.FORBIDDEN,
                    "Boards access is not allowed for this Context/Space.",
                    Map.of(
                            "reason", decision.reason(),
                            "contextId", principalContext.contextId(),
                            "permission", permission.name().toLowerCase())));
        }
        return principalContext;
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
        String contextId = claimOrDefault(jwt, "weave_context_id", "context_id", DEFAULT_CONTEXT_ID);
        return new PrincipalContext(tenantId, contextId, principalRef);
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

    private String capabilityFor(ContextPermission permission) {
        return permission == ContextPermission.VIEW ? "boards.read" : "boards.update_task";
    }

    private String operationFor(ContextPermission permission) {
        return permission == ContextPermission.VIEW ? "read-workspace" : "mutate-task";
    }

    private record PrincipalContext(String tenantId, String contextId, String principalRef) {
    }

    private void requireEnabled() {
        try {
            runtimeGuard.requireEnabled();
        } catch (BoardsException exception) {
            throw apiError(exception);
        }
    }

    private TaskStatus parseStatus(String value) {
        if (value == null || value.isBlank()) {
            throw apiError(new BoardsException(
                    BoardsErrorCode.VALIDATION,
                    "Task status is required for Boards status updates.",
                    Map.of("field", "status")));
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        for (TaskStatus status : TaskStatus.values()) {
            if (status.contractName().equals(normalized) || status.name().toLowerCase(Locale.ROOT).equals(normalized)) {
                return status;
            }
        }
        throw apiError(new BoardsException(
                BoardsErrorCode.VALIDATION,
                "Task status is not supported by the Boards workspace facade.",
                Map.of("field", "status", "supportSafe", "true")));
    }

    private void publishTaskWriteAudit(PrincipalContext principal, AuditAction action, String subject, Map<String, Object> payload) {
        Instant timestamp = Instant.now();
        Map<String, Object> auditPayload = supportSafeAuditPayload(payload);
        auditPayload.put("supportSafe", true);
        AuditWriteGate.publishRequired(auditEventPublisher, new AuditEvent(
                principal.tenantId(),
                principal.contextId(),
                principal.principalRef(),
                "weave:boards-workspace",
                action,
                timestamp,
                action.wireName() + ":" + supportSafeAuditString(subject) + ":" + timestamp,
                AuditRedactionLevel.SUPPORT_SAFE,
                auditPayload));
    }

    private Map<String, Object> supportSafeAuditPayload(Map<String, Object> payload) {
        Map<String, Object> safePayload = new LinkedHashMap<>();
        payload.forEach((key, value) -> safePayload.put(key,
                value instanceof String stringValue && !"mappingRef".equals(key) ? supportSafeAuditString(stringValue) : value));
        return safePayload;
    }

    private String supportSafeAuditString(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (trimmed.length() > 160
                || normalized.contains("://")
                || normalized.contains("authorization")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("password")
                || normalized.contains("apikey")
                || normalized.contains("api_key")
                || normalized.contains("cookie")) {
            return "[redacted:unsafe-ref]";
        }
        return trimmed;
    }

    private String sourceFor(ProviderKind provider) {
        return "weave-boards-tasks-facade";
    }

    private BoardsProjectResponse projectResponse(WeaveProject project) {
        return new BoardsProjectResponse(
                project.id(),
                project.name(),
                project.visibility(),
                project.memberRefs(),
                mappingRef("project", project.id()));
    }

    private BoardsBoardResponse boardResponse(Board board) {
        return new BoardsBoardResponse(
                board.id(),
                board.projectId(),
                board.name(),
                board.description(),
                board.columns().stream().map(column -> new BoardsColumnResponse(
                        column.id(),
                        column.boardId(),
                        column.name(),
                        column.position(),
                        column.semanticStatus(),
                        column.wipLimit(),
                        mappingRef("column", column.id()))).toList(),
                board.archived(),
                mappingRef("board", board.id()));
    }

    private BoardsTaskResponse taskResponse(TaskItem task) {
        return new BoardsTaskResponse(
                task.id(),
                task.boardId(),
                task.columnId(),
                task.title(),
                task.description(),
                task.status(),
                task.position(),
                task.assigneeRefs(),
                task.labelRefs(),
                task.decisionRefs(),
                task.priority(),
                task.startAt(),
                task.dueAt(),
                task.completedAt(),
                task.updatedAt(),
                mappingRef("task", task.id()));
    }

    private BoardsSyncMetadataResponse syncMetadata(
            String source,
            Map<String, String> nextCursors,
            List<WeaveProject> projects,
            List<Board> boards,
            List<TaskItem> tasks) {
        return new BoardsSyncMetadataResponse(
                "boards-tasks",
                "canonical-domain-facade",
                true,
                true,
                true,
                nextCursors,
                mappingRefs(projects, boards, tasks),
                "coming_later",
                lastSyncedAt(projects, boards, tasks));
    }

    private Map<String, String> mappingRefs(List<WeaveProject> projects, List<Board> boards, List<TaskItem> tasks) {
        Map<String, String> refs = new LinkedHashMap<>();
        projects.forEach(project -> refs.put(project.id(), mappingRef("project", project.id())));
        boards.forEach(board -> refs.put(board.id(), mappingRef("board", board.id())));
        tasks.forEach(task -> refs.put(task.id(), mappingRef("task", task.id())));
        return refs;
    }

    private String mappingRef(String type, String canonicalId) {
        return "provider-mapping://boards/" + type + "/" + stableAuditRef(canonicalId);
    }

    private String stableAuditRef(String source) {
        if (source == null || source.isBlank()) {
            return "unknown";
        }
        return Integer.toHexString(source.hashCode());
    }

    private void addCursor(Map<String, String> nextCursors, String key, String cursor) {
        if (cursor != null && !cursor.isBlank()) {
            String normalized = cursor.toLowerCase(Locale.ROOT);
            if (cursor.length() > 512
                    || normalized.contains("://")
                    || normalized.contains("token")
                    || normalized.contains("secret")
                    || normalized.contains("password")
                    || normalized.contains("apikey")
                    || normalized.contains("api_key")) {
                throw new BoardsException(
                        BoardsErrorCode.VALIDATION,
                        "Boards adapter returned an unsafe pagination cursor and the workspace response was blocked.",
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
            case OFFLINE, PROVIDER_UNAVAILABLE, UNSUPPORTED_CAPABILITY -> HttpStatus.SERVICE_UNAVAILABLE;
            case UNKNOWN -> HttpStatus.BAD_GATEWAY;
        };
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("module", "boards");
        details.put("workspace", true);
        details.put("releaseStatus", "active-dogfood-production");
        details.putAll(exception.details());
        return new ApiErrorException(status, "boards-" + exception.code().contractName(), exception.getMessage(), details);
    }
}
